import dependencies.Config;
import dependencies.Emailer;
import dependencies.Logger;
import dependencies.Project;
import io.vavr.collection.Traversable;
import io.vavr.control.Try;

import java.util.List;
import java.util.function.Function;

public class Pipeline {
  private final Config config;
  private final Emailer emailer;
  private final Logger log;

  public Pipeline(Config config, Emailer emailer, Logger log) {
    this.config = config;
    this.emailer = emailer;
    this.log = log;
  }

  public void run(Project project) {
    Try.traverse(steps(Pipeline::runTests, Pipeline::runDeployment),
                 f -> f.apply(project).andThen(log::info))
       .onFailure(this::logError)
       .map(Traversable::last)
       .recover(Throwable::getMessage)
       .andThen(this::sendNotification);
  }

  private static Try<String> runTests(Project project) {
    if (!project.hasTests()) {
      return Try.success("No tests");
    }
    if (isSuccessful(project.runTests())) {
      return Try.success("Tests passed");
    }
    return Try.failure(new TestsFailedException());
  }

  private static Try<String> runDeployment(Project project) {
    if (isSuccessful(project.deploy())) {
      return Try.success("Deployment successful");
    }
    return Try.failure(new DeploymentFailedException());
  }

  private static boolean isSuccessful(String s) {
    return "success".equals(s);
  }

  @SafeVarargs
  private static List<Function<Project, Try<String>>> steps(Function<Project, Try<String>>... fs) {
    return List.of(fs);
  }

  private void sendNotification(String message) {
    if (config.sendEmailSummary()) {
      log.info("Sending email");
      emailer.send(message);
    } else {
      log.info("Email disabled");
    }
  }

  private void logError(Throwable e) {
    log.error(e.getMessage());
  }

  private static class TestsFailedException extends RuntimeException {
    public TestsFailedException() {
      super("Tests failed");
    }
  }

  private static class DeploymentFailedException extends RuntimeException {
    public DeploymentFailedException() {
      super("Deployment failed");
    }
  }
}
