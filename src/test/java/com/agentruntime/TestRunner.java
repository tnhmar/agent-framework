package com.agentruntime;

import org.junit.jupiter.api.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/**
 * Minimal JUnit 5-compatible test runner using pure JDK reflection.
 * Discovers all test classes, runs @BeforeEach + @Test methods,
 * and reports pass/fail counts.
 */
public class TestRunner {

    static int passed = 0, failed = 0, skipped = 0;
    static List<String> failures = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println("  agent-runtime TEST SUITE");
        System.out.println("═══════════════════════════════════════════════════════");

        String[] testClasses = {
            "com.agentruntime.ActionValidationPipelineTest",
            "com.agentruntime.AgentRuntimeBuilderTest",
            "com.agentruntime.AuditConsistencyCheckerTest",
            "com.agentruntime.ConflictResolverTest",
            "com.agentruntime.CosineSimilarityTest",
            "com.agentruntime.DefaultReasoningModuleTest",
            "com.agentruntime.DelegationContractTest",
            "com.agentruntime.InfrastructureTest",
            "com.agentruntime.InMemoryRunRepositoryTest",
            "com.agentruntime.ModelClientTest",
            "com.agentruntime.ObservabilityBusTest",
            "com.agentruntime.OrchestratorIntegrationTest",
            "com.agentruntime.ProtocolsTest",
            "com.agentruntime.ResearchAgentStudyCaseTest",
            "com.agentruntime.RunAggregateTest",
            "com.agentruntime.SecurityLayerTest",
            "com.agentruntime.SessionLifecycleTest",
            "com.agentruntime.SharedMemoryStoreTest",
            "com.agentruntime.StructuredEventExporterTest",
            "com.agentruntime.TenantPolicyEngineTest",
            "com.agentruntime.TokenWindowMemoryPolicyTest",
            "com.agentruntime.GoalStackTest",
            "com.agentruntime.BeliefStateTest",
            "com.agentruntime.UserModelAndCheckpointTest",
            "com.agentruntime.ConsolidationAndScoringTest",
            "com.agentruntime.ToolContractAndInfraTest",
            "com.agentruntime.ProductionReadinessTest",
        };

        for (String className : testClasses) {
            runTestClass(className);
        }

        System.out.println("\n═══════════════════════════════════════════════════════");
        System.out.printf("  RESULTS: %d passed, %d failed, %d skipped%n", passed, failed, skipped);
        System.out.println("═══════════════════════════════════════════════════════");

        if (!failures.isEmpty()) {
            System.out.println("\nFAILURES:");
            failures.forEach(f -> System.out.println("  ✗ " + f));
        }

        if (failed > 0) System.exit(1);
    }

    static void runTestClass(String className) {
        System.out.println("\n── " + className.replaceAll(".*\\.", "") + " ──");
        try {
            Class<?> cls = Class.forName(className);
            // Find @BeforeEach and @AfterEach methods
            List<Method> beforeEach = new ArrayList<>();
            List<Method> afterEach  = new ArrayList<>();
            List<Method> tests      = new ArrayList<>();

            for (Method m : cls.getDeclaredMethods()) {
                m.setAccessible(true);
                if (m.isAnnotationPresent(BeforeEach.class)) beforeEach.add(m);
                if (m.isAnnotationPresent(AfterEach.class))  afterEach.add(m);
                if (m.isAnnotationPresent(Test.class))       tests.add(m);
            }

            // Sort tests by @Order if present, then alphabetically
            tests.sort((a, b) -> {
                Order oa = a.getAnnotation(Order.class);
                Order ob = b.getAnnotation(Order.class);
                if (oa != null && ob != null) return Integer.compare(oa.value(), ob.value());
                if (oa != null) return -1;
                if (ob != null) return  1;
                return a.getName().compareTo(b.getName());
            });

            for (Method test : tests) {
                Object instance = cls.getDeclaredConstructor().newInstance();
                try {
                    for (Method b : beforeEach) b.invoke(instance);
                    test.invoke(instance);
                    for (Method a : afterEach)  a.invoke(instance);
                    System.out.printf("  ✓ %s%n", test.getName());
                    passed++;
                } catch (InvocationTargetException ite) {
                    Throwable cause = ite.getCause();
                    String msg = test.getName() + ": " + cause.getMessage();
                    System.out.printf("  ✗ %s%n    %s: %s%n",
                        test.getName(), cause.getClass().getSimpleName(), cause.getMessage());
                    failures.add(className.replaceAll(".*\\.","") + "." + msg);
                    failed++;
                } catch (Exception e) {
                    String msg = test.getName() + ": " + e.getMessage();
                    System.out.printf("  ✗ %s%n    %s%n", test.getName(), e.getMessage());
                    failures.add(className.replaceAll(".*\\.","") + "." + msg);
                    failed++;
                }
            }
        } catch (Exception e) {
            System.out.println("  ERROR loading class: " + e.getMessage());
            failures.add(className + " [class load]: " + e.getMessage());
            failed++;
        }
    }
}
