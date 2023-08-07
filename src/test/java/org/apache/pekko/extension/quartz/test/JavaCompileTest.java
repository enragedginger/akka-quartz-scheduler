package org.apache.pekko.extension.quartz.test;


import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.extension.quartz.QuartzSchedulerExtension;

public class JavaCompileTest {
  
  @SuppressWarnings("unused") // compile only spec
  public void shouldBeAccessibleFromJava() throws Exception {
    final ActorSystem system = null;
    final QuartzSchedulerExtension t = QuartzSchedulerExtension.get(system);
  }
}
