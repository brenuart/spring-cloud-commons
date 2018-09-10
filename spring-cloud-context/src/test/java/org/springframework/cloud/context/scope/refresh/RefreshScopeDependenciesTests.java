/*
 * Copyright 2013-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.context.scope.refresh;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.PreDestroy;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


public class RefreshScopeDependenciesTests {
	
	@Rule
	public ExpectedException expected = ExpectedException.none();

	private static List<Object> createOrder = new ArrayList<>();
	private static List<Object> shutdownOrder = new ArrayList<>();
	private static boolean invokeBDuringShutdown = false;
	
	private static Log logger = LogFactory.getLog("**TEST**");

	
	@Before
	public void setup() {
		createOrder.clear();
		shutdownOrder.clear();
		invokeBDuringShutdown = false;
	}
	
	
	/*
	 * Demonstrate that the bean name has an impact in the order target beans are shutdown.
	 * 
	 * Scenario:
	 * - A depends on B, 
	 * - A appears before B in the configuration
	 * - RefreshScope is configured to eagerly initialize target beans on ContextRefreshedEvent.
	 * 
	 * Expected:
	 *   Target A should be disposed *before* B, because of its dependency on it.
	 *   Targets can be created in any order since their inter-dependencies are resolved through the scoped-proxies.
	 * 
	 * Actual:
	 *   Target beans are currently shutdown in unpredictable order which seems to be dependent on the position of their 
	 *   definition in the configuration and their names.
	 */
	@Test
	public void shutdown_beanname_1() {
		shutdown_beanname(Application_1.class);
	}
	@Test
	public void shutdown_beanname_2() {
		shutdown_beanname(Application_2.class);
	}
	
	private void shutdown_beanname(Class<?> config) {
		try(AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				config, PropertyPlaceholderAutoConfiguration.class, RefreshAutoConfiguration.class)) 
		{
		}

		assertThat(createOrder).size().isEqualTo(2);
		assertThat(shutdownOrder).size().isEqualTo(2);
		
		assertThat(shutdownOrder.indexOf(A.class))
			.as("A depends on B and should be disposed *before* B (shutdown order was: "+shutdownOrder+")")
			.isLessThan(shutdownOrder.indexOf(B.class));

		
		// Uncomment if you feel this should be guaranteed as well..
		//
		//		assertThat(createOrder.indexOf(A.class))
		//			.as("A depends on B and should be created *after* B (create order was: "+createOrder+")")
		//			.isGreaterThan(createOrder.indexOf(B.class));
	}

	
	
	// ------------------------------------------------------------------------------------------------
	
	/*
	 * Disable eager initialization of target beans.
	 * Grab references from context -> no target should be created.
	 */
	@Test
	public void shutdown_eagerfalse_noinstance_1() {
		shutdown_eagerfalse_noinstance(Application_1.class);
	}
	@Test
	public void shutdown_eagerfalse_noinstance_2() {
		shutdown_eagerfalse_noinstance(Application_2.class);
	}
	
	private void shutdown_eagerfalse_noinstance(Class<?> config) {
		try(AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				config, DisableEagerInit.class, PropertyPlaceholderAutoConfiguration.class, RefreshAutoConfiguration.class)) 
		{
			context.getBean(A.class);
			context.getBean(B.class);
		}
		
		assertThat(createOrder).isEmpty();
		assertThat(shutdownOrder).isEmpty();
	}
	
	
	
	/*
	 * Disable eager initialization of target beans.
	 * Invoke method on A that calls B.
	 * Whatever the execution path, A must be shutdown before B. 
	 */
	@Test
	public void shutdown_eagerfalse_a_calls_b_1() {
		shutdown_eagerfalse_a_calls_b(Application_1.class);
	}
	@Test
	public void shutdown_eagerfalse_a_calls_b_2() {
		shutdown_eagerfalse_a_calls_b(Application_2.class);
	}
	
	private void shutdown_eagerfalse_a_calls_b(Class<?> config) {
		try(AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				config, DisableEagerInit.class, PropertyPlaceholderAutoConfiguration.class, RefreshAutoConfiguration.class)) 
		{
			A a = context.getBean(A.class);
			a.callB();
		}
		
		assertThat(createOrder).size().isEqualTo(2);
		assertThat(shutdownOrder).size().isEqualTo(2);
		assertThat(shutdownOrder.indexOf(A.class))
			.as("A depends on B and should be disposed *before* B (shutdown order was: "+shutdownOrder+")")
			.isLessThan(shutdownOrder.indexOf(B.class));
	}
	
	
	/*
	 * Disable eager initialization of target beans.
	 * Invoke method on B then invoke method on A.
	 */
	@Test
	public void shutdown_eagerfalse_invokeB_beforeA_1() {
		shutdown_eagerfalse_invokeB_beforeA(Application_1.class);
	}
	@Test
	public void shutdown_eagerfalse_invokeB_beforeA_2() {
		shutdown_eagerfalse_invokeB_beforeA(Application_2.class);
	}
	
	private void shutdown_eagerfalse_invokeB_beforeA(Class<?> config) {
		try(AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				config, DisableEagerInit.class, PropertyPlaceholderAutoConfiguration.class, RefreshAutoConfiguration.class)) 
		{
			context.getBean(B.class).toString();

			A a = context.getBean(A.class);
			a.callB();
		}
		
		assertThat(createOrder).size().isEqualTo(2);
		assertThat(shutdownOrder).size().isEqualTo(2);
		assertThat(shutdownOrder.indexOf(A.class))
			.as("A depends on B and should be disposed *before* B (shutdown order was: "+shutdownOrder+")")
			.isLessThan(shutdownOrder.indexOf(B.class));
	}
	
	
	
	/*
	 * A calls B during its shutdown
	 */
	@Test
	public void shutdown_eagerfalse_invokeB_during_shutdown_1() {
		shutdown_eagerfalse_invokeB_during_shutdown(Application_1.class);
	}
	@Test
	public void shutdown_eagerfalse_invokeB_during_shutdown_2() {
		shutdown_eagerfalse_invokeB_during_shutdown(Application_2.class);
	}
	
	private void shutdown_eagerfalse_invokeB_during_shutdown(Class<?> config) {
		invokeBDuringShutdown = true;
		
		try(AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				config, DisableEagerInit.class, PropertyPlaceholderAutoConfiguration.class, RefreshAutoConfiguration.class)) 
		{
			A a = context.getBean(A.class);
			a.callB();
		}
		
		assertThat(createOrder).size().isEqualTo(2);
		assertThat(shutdownOrder).size().isEqualTo(2);
		assertThat(shutdownOrder.indexOf(A.class))
			.as("A depends on B and should be disposed *before* B (shutdown order was: "+shutdownOrder+")")
			.isLessThan(shutdownOrder.indexOf(B.class));
	}
	
	
	
	// ------------------------------------------------------------------------------------------------

	
	
	@Configuration
	static class DisableEagerInit {
		@Bean
		public org.springframework.cloud.context.scope.refresh.RefreshScope refreshScope() {
			org.springframework.cloud.context.scope.refresh.RefreshScope refreshScope = new org.springframework.cloud.context.scope.refresh.RefreshScope();
			refreshScope.setEager(false);
			return refreshScope;
		}
	}


	@Configuration
	protected static class Application_1 {
		
		@Bean
		@RefreshScope
		public A beanA(B b) {
			return new A(b);
		}

		@Bean
		@RefreshScope
		public B beanB() {
			return new B();
		}

		public static void main(String[] args) {
			SpringApplication.run(Application_1.class, args);
		}
	}
	
	@Configuration
	protected static class Application_2 {
		
		@Bean
		@RefreshScope
		public B aaa_beanB() {
			return new B();
		}
		
		@Bean
		@RefreshScope
		public A zzz_beanA(B b) {
			return new A(b);
		}
		
		
		public static void main(String[] args) {
			SpringApplication.run(Application_2.class, args);
		}
	}
	
	
	static abstract class Base {
		public Base() {
			logger.info("New instance of "+this.getClass());
			createOrder.add(this.getClass());
		}
		
		@PreDestroy
		public void shutdown() {
			logger.info("Shutdown of "+this.getClass());
			shutdownOrder.add(this.getClass());
		}
	}
	
	static class A extends Base {
		private B b;
		
		public A(B b) {
			super();
			this.b = b;
		}
		
		public void callB() {
			b.sayHello();
		}
		
		@Override
		public void shutdown() {
			if (invokeBDuringShutdown) {
				callB();
			}
			super.shutdown();
		}
	}
	
	static class B extends Base {
		public void sayHello() {
			logger.info("Hello from "+this.getClass());
		}
	}
}
