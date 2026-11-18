package com.example.base.controller;

import com.example.base.entity.Account;
import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import com.example.base.security.CustomUserDetails;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.mockito.Answers;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Controllers only translate HTTP to a service call. One dynamic test per endpoint: invoke the handler with
 * mocked collaborators and assert it delegates to them and answers 2xx. Request mapping/validation is covered
 * by the integration tests.
 */
class ControllerDelegationTest {

    // Handlers with their own branching are tested one by one in ControllerLogicTest.
    private static final Set<String> HAND_WRITTEN = Set.of(
            "ChatAttachmentController.download", "ChatAttachmentController.limits", "OfficeWorkflowController.receive",
            "ChatLocationAddressController.address");
    private static final Set<String> NUMERIC_TEXT = Set.of("page", "size", "id");
    private static final CustomUserDetails USER;

    static {
        Account a = new Account();
        a.setId(1L);
        a.setEmail("manager@gmail.com");
        a.setRole(AccountRole.MANAGER);
        a.setStatus(AccountStatus.ACTIVE);
        USER = new CustomUserDetails(a);
    }

    @TestFactory
    Stream<DynamicTest> everyEndpointDelegatesToItsService() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Class<?>> controllers = new ArrayList<>();
        for (var bd : scanner.findCandidateComponents("com.example.base.controller")) {
            controllers.add(Class.forName(bd.getBeanClassName()));
        }
        controllers.sort(Comparator.comparing(Class::getSimpleName));
        assertThat(controllers).isNotEmpty();

        return controllers.stream().flatMap(type -> Arrays.stream(type.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> AnnotatedElementUtils.hasAnnotation(m, RequestMapping.class))
                .filter(m -> !HAND_WRITTEN.contains(type.getSimpleName() + "." + m.getName()))
                .sorted(Comparator.comparing(Method::getName).thenComparing(Method::getParameterCount))
                .map(m -> DynamicTest.dynamicTest(type.getSimpleName() + "." + m.getName(), () -> invoke(type, m))));
    }

    private static void invoke(Class<?> type, Method method) throws Exception {
        Constructor<?> ctor = Arrays.stream(type.getConstructors())
                .max(Comparator.comparingInt(Constructor::getParameterCount)).orElseThrow();
        Object[] deps = Arrays.stream(ctor.getParameterTypes())
                .map(t -> mock(t, withSettings().defaultAnswer(Answers.RETURNS_MOCKS)))
                .toArray();
        Object controller = ctor.newInstance(deps);

        Object[] args = Arrays.stream(method.getParameters()).map(ControllerDelegationTest::argument).toArray();
        Object result;
        try {
            result = method.invoke(controller, args);
        } catch (InvocationTargetException e) {
            throw new AssertionError(method + " threw " + e.getCause(), e.getCause());
        }

        boolean delegated = Arrays.stream(deps).anyMatch(d -> !mockingDetails(d).getInvocations().isEmpty());
        assertThat(delegated).as("%s calls a collaborator", method.getName()).isTrue();
        if (result instanceof ResponseEntity<?> response) {
            assertThat(response.getStatusCode().is2xxSuccessful()).as("%s answers 2xx", method.getName()).isTrue();
        }
    }

    private static Object argument(Parameter p) {
        Class<?> t = p.getType();
        if (t == CustomUserDetails.class) return USER;
        if (t == long.class || t == Long.class) return 1L;
        if (t == int.class || t == Integer.class) return 1;
        if (t == boolean.class || t == Boolean.class) return false;
        if (t == String.class) return NUMERIC_TEXT.contains(p.getName()) || p.getName().endsWith("Id") ? "1" : "";
        if (t == UUID.class) return UUID.randomUUID();
        if (t == MultipartFile.class) return mock(MultipartFile.class);
        if (List.class.isAssignableFrom(t)) return List.of();
        if (Map.class.isAssignableFrom(t)) return Map.of();
        if (t.getName().startsWith("jakarta.servlet.http.HttpServletRequest")) return new MockHttpServletRequest();
        if (t.getName().startsWith("jakarta.servlet.http.HttpServletResponse")) return new MockHttpServletResponse();
        if (t.isEnum()) return t.getEnumConstants()[0];
        if (t.isPrimitive()) return 0;
        return mock(t, withSettings().defaultAnswer(Answers.RETURNS_MOCKS));
    }
}
