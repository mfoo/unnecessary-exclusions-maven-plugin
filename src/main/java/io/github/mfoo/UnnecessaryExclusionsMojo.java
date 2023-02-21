package io.github.mfoo;

import java.util.ArrayList;
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

    /**
     * Used to filter for classes that will be on the classpath at runtime. We don't need test-scoped dependencies,
     * those aren't inherited between projects.
     */
    private static final DependencyFilter CLASSPATH_FILTER =
            DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE, JavaScopes.RUNTIME);

    private static boolean dependencyMatches(String artifactId, String groupId, Artifact transitiveDependency) {
        return (artifactId.equals("*") || transitiveDependency.getArtifactId().equals(artifactId))
                && (groupId.equals("*") || transitiveDependency.getGroupId().equals(groupId));
    }

    /**
     * Iterate over the dependencies for the project fetching their exclusions list and transitive dependencies. If the dependency has any exclusions that aren't a dependency of
     * that dependency, log a warning.
     */
    @Override
    public void execute() {
        project.getDependencies().stream()
                .filter(d -> !d.getExclusions().isEmpty())
                .forEach(dependency -> {
                    List<ArtifactResult> artifactResults = getDependencies(dependency);

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
                });
    }

    /**
     * Use the Maven project/dependency APIs to fetch the list of dependencies for this dependency. This will make API
     * calls to the configured repositories.
     */
    private List<ArtifactResult> getDependencies(org.apache.maven.model.Dependency projectDependency) {

        List<ArtifactResult> results = new ArrayList<>();

        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRoot(new Dependency(
                new DefaultArtifact(
                        projectDependency.getGroupId(),
                        projectDependency.getArtifactId(),
                        "pom",
                        projectDependency.getVersion()),
                projectDependency.getScope()));

        for (RemoteRepository remoteRepo : remoteRepos) {
            collectRequest.addRepository(remoteRepo);
        }

        DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, CLASSPATH_FILTER);

        try {
            results.addAll(repoSystem
                    .resolveDependencies(repoSession, dependencyRequest)
                    .getArtifactResults());
        } catch (DependencyResolutionException e) {
            getLog().error(String.format(
                    "Could not fetch details for %s:%s",
                    projectDependency.getGroupId(), projectDependency.getArtifactId()));
        }

        return results;
    }
}
