/*
 * Copyright (C) 2017 DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.loader.tests.ccm.annotations;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.datastax.loader.tests.ccm.CCMCluster;
import com.datastax.loader.tests.ccm.DefaultCCMCluster;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * A class annotated with this annotation should obtain its {@link CCMCluster} instance from the
 * specified factory method.
 */
@Retention(RUNTIME)
@Target(TYPE)
public @interface CCMFactoryMethod {

  /**
   * Returns the name of the method that should be invoked to obtain a {@link
   * DefaultCCMCluster.Builder} instance.
   *
   * <p>This method should be declared in {@link #factoryClass()}, or if that attribute is not set,
   * it will be looked up on the test class itself.
   *
   * <p>The method should not have parameters. It can be static or not, and have any visibility.
   *
   * <p>By default, a {@link DefaultCCMCluster.Builder} instance is obtained by parsing other
   * attributes of this annotation.
   *
   * @return The name of the method that should be invoked to obtain a {@link
   *     DefaultCCMCluster.Builder} instance.
   */
  String value() default "";

  /**
   * Returns the name of the class that should be invoked to obtain a {@link
   * DefaultCCMCluster.Builder} instance.
   *
   * <p>This class should contain a method named after {@link #value()}; if this attribute is not
   * set, it will default to the test class itself.
   *
   * @return The name of the class that should be invoked to obtain a {@link
   *     DefaultCCMCluster.Builder} instance.
   */
  Class<?> factoryClass() default TestClass.class;

  /** A dummy class that acts like a placeholder for the actual test class being executed. */
  final class TestClass {}
}
