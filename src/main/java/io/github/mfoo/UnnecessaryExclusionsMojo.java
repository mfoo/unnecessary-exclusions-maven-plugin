package io.github.mfoo;

import java.util.List;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;

@Mojo(name = "analyze", defaultPhase = LifecyclePhase.VERIFY)
public class UnnecessaryExclusionsMojo extends AbstractMojo {
    /**
     * The Maven Project that the plugin is being executed on. Used for accessing e.g. the list of
     * dependencies.
     */
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    /**
     * The entry point to Aether, i.e. the component doing all the work.
     *
     * @component
     */
    @Component
    private RepositorySystem repoSystem;

    /**
     * The current repository/network configuration of Maven.
     */
    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repoSession;

    /**
     * The project's remote repositories to use for the resolution of plugins and their dependencies.
     */
    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
    private List<RemoteRepository> remoteRepos;

    private static boolean dependencyMatches(String artifactId, String groupId, Artifact transitiveDependency) {
        return (artifactId.equals("*") || transitiveDependency.getArtifactId().equals(artifactId))
                && (groupId.equals("*") || transitiveDependency.getGroupId().equals(groupId));
    }

    @Override
    public void execute() {
        // Iterate over dependencies, checking if any have exclusions
        project.getDependencies().forEach(dependency -> {
            try {
                if (dependency.getExclusions().isEmpty()) {
                    return;
                }

                List<ArtifactResult> artifactResults = getDependenciesIncludingTransitive(dependency);

                // For each exclusion, iterate over the transitive dependencies. If none match the exclusion, shout
                dependency.getExclusions().forEach(dependencyExclusion -> {
                    String artifactId = dependencyExclusion.getArtifactId();
                    String groupId = dependencyExclusion.getGroupId();

                    boolean noneMatched = artifactResults.stream()
                            .noneMatch(transitiveDependency ->
                                    dependencyMatches(artifactId, groupId, transitiveDependency.getArtifact()));

                    if (noneMatched) {
                        getLog().warn(String.format(
                                "Dependency %s:%s excludes %s:%s unnecessarily",
                                dependency.getGroupId(), dependency.getArtifactId(), groupId, artifactId));
                    }
                });
            } catch (DependencyResolutionException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private List<ArtifactResult> getDependenciesIncludingTransitive(org.apache.maven.model.Dependency projectDependency)
            throws DependencyResolutionException {
        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRoot(new Dependency(
                new DefaultArtifact(String.format(
                        "%s:%s:%s:%s",
                        projectDependency.getGroupId(),
                        projectDependency.getArtifactId(),
                        projectDependency.getClassifier(),
                        projectDependency.getVersion())),
                projectDependency.getScope()));

        for (RemoteRepository remoteRepo : remoteRepos) {
            collectRequest.addRepository(remoteRepo);
        }
        DependencyFilter classpathFilter =
                DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE, JavaScopes.RUNTIME, JavaScopes.TEST);
        DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, classpathFilter);

        return repoSystem.resolveDependencies(repoSession, dependencyRequest).getArtifactResults();
    }
}
