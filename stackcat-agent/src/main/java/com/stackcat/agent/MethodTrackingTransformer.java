package com.stackcat.agent;

import com.stackcat.agent.util.PackageFilter;
import lombok.extern.slf4j.Slf4j;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.AnnotationVisitor;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

/**
 * @author zhenzijun
 */
@Slf4j
public class MethodTrackingTransformer implements ClassFileTransformer {
    private static final String TRACKING_BRIDGE_CLASS = "com/stackcat/agent/util/MethodTrackingBridge";
    private static final PackageFilter PACKAGE_FILTER;

    static {
        // Initialize package filter from system properties
        String excludePackages = System.getProperty("stackcat.filter.packages.exclude", "");
        String includePackages = System.getProperty("stackcat.filter.packages.include", "");

        // If no configuration is provided, default to tracking com.stackcat package
        // Note: com.stackcat.agent.* will be excluded automatically by PackageFilter
        if (includePackages.isEmpty() && excludePackages.isEmpty()) {
            includePackages = "com.stackcat";
        }

        PACKAGE_FILTER = createPackageFilter(excludePackages, includePackages);
    }

    private static PackageFilter createPackageFilter(String excludeStr, String includeStr) {
        java.util.List<String> excludeList = excludeStr.isEmpty() ?
                java.util.List.of() : java.util.Arrays.asList(excludeStr.split(","));
        java.util.List<String> includeList = includeStr.isEmpty() ?
                java.util.List.of() : java.util.Arrays.asList(includeStr.split(","));
        return new PackageFilter(excludeList, includeList);
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classFileBuffer) {
        try {
            String dotClassName = className.replace('/', '.');

            if ("org/apache/ibatis/executor/SimpleExecutor".equals(className)) {
                try {
                    ClassReader cr = new ClassReader(classFileBuffer);
                    ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
                    ClassVisitor cv = new MyBatisExecutorTrackingClassVisitor(cw, className);
                    cr.accept(cv, ClassReader.EXPAND_FRAMES);
                    byte[] transformed = cw.toByteArray();
                    log.debug("MyBatis SimpleExecutor intercepted successfully: {}", className);
                    return transformed;
                } catch (Exception e) {
                    log.error("ERROR intercepting MyBatis SimpleExecutor {}", className, e);
                    return null;
                }
            }

            // Skip only Agent's own classes to avoid infinite recursion
            // Allow application classes (controller, service, etc.) to be tracked
            // Skip all classes in com.stackcat.agent.* package (including util subpackage)
            if (className.startsWith("com/stackcat/agent/")) {
                    return null;
            }

            boolean shouldTrack = PACKAGE_FILTER.shouldTrack(dotClassName);
            if (!shouldTrack) {
                return null;
            }

            // Special handling for Controller classes - MUST check BEFORE skipping Spring classes
            // Intercept classes with package name containing "controller" (case-insensitive)
            String lowerClassName = className.toLowerCase();
            if (lowerClassName.contains("controller")) {
                try {
                    ClassReader cr = new ClassReader(classFileBuffer);
                    ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
                    ClassVisitor cv = new ControllerTrackingClassVisitor(cw, className);
                    cr.accept(cv, ClassReader.EXPAND_FRAMES);
                    byte[] transformed = cw.toByteArray();
                    log.debug("Controller intercepted successfully: {}", className);
                    return transformed;
                } catch (Exception e) {
                log.error("ERROR intercepting Controller {}", className, e);
                return null;
            }
            }

            try {
                ClassReader cr = new ClassReader(classFileBuffer);
                ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
                ClassVisitor cv = new MethodTrackingClassVisitor(cw, className);
                cr.accept(cv, ClassReader.EXPAND_FRAMES);
                byte[] transformed = cw.toByteArray();
                log.debug("Class transformed: {}", dotClassName);
                return transformed;
            } catch (Exception e) {
                log.error("Error transforming class {}: {}", dotClassName, e.getMessage(), e);
                // Silently fail - don't break application startup
                return null;
            }
        } catch (Throwable t) {
            // Catch all errors to prevent breaking application startup
            log.error("Unexpected error transforming {}: {}", className, t.getMessage(), t);
            return null;
        }
    }

    private static class MethodTrackingClassVisitor extends ClassVisitor {
        private final String className;

        public MethodTrackingClassVisitor(ClassVisitor cv, String className) {
            super(Opcodes.ASM9, cv);
            this.className = className;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            MethodVisitor mv = cv.visitMethod(access, name, descriptor, signature, exceptions);
            String dotClassName = className.replace('/', '.');

            // Skip constructors, static initializers, and synthetic methods
            if ("<init>".equals(name) || "<clinit>".equals(name) ||
                    (access & Opcodes.ACC_SYNTHETIC) != 0 || dotClassName.contains("CGLIB")) {
                return mv;
            }

            log.debug("Found method {}.{}", dotClassName, name);

            return new MethodTrackingMethodVisitor(mv, className, name, access);
        }

        @Override
        public void visitEnd() {
            super.visitEnd();
        }
    }

    private static class MethodTrackingMethodVisitor extends MethodVisitor {
        private final String className;
        private final String methodName;

        public MethodTrackingMethodVisitor(MethodVisitor mv, String className,
                                           String methodName, int access) {
            super(Opcodes.ASM9, mv);
            this.className = className;
            this.methodName = methodName;
        }

        @Override
        public void visitCode() {
            String dotClassName = className.replace('/', '.');

            mv.visitCode();

            // Add debug logging for Service/Controller/Repository methods
            // Note: We'll log in recordMethodCall instead to avoid bytecode complexity

            // Call tracking bridge to record method entry
            mv.visitLdcInsn(dotClassName);
            mv.visitLdcInsn(methodName);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                    TRACKING_BRIDGE_CLASS,
                    "getCurrentDepth",
                    "()I",
                    false);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                    TRACKING_BRIDGE_CLASS,
                    "recordMethodCall",
                    "(Ljava/lang/String;Ljava/lang/String;I)V",
                    false);

            // Increment depth
            mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                    TRACKING_BRIDGE_CLASS,
                    "incrementDepth",
                    "()V",
                    false);
        }

        @Override
        public void visitEnd() {
            super.visitEnd();
        }

        @Override
        public void visitInsn(int opcode) {
            if (opcode == Opcodes.RETURN || opcode == Opcodes.IRETURN ||
                    opcode == Opcodes.LRETURN || opcode == Opcodes.FRETURN ||
                    opcode == Opcodes.DRETURN || opcode == Opcodes.ARETURN) {

                // Decrement depth before return
                mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                        TRACKING_BRIDGE_CLASS,
                        "decrementDepth",
                        "()V",
                        false);
            }
            mv.visitInsn(opcode);
        }
    }

    // Special visitor for Controller classes
    private static class ControllerTrackingClassVisitor extends ClassVisitor {
        private final String className;

        public ControllerTrackingClassVisitor(ClassVisitor cv, String className) {
            super(Opcodes.ASM9, cv);
            this.className = className;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            MethodVisitor mv = cv.visitMethod(access, name, descriptor, signature, exceptions);

            // Skip constructors, static initializers, and synthetic methods
            if ("<init>".equals(name) || "<clinit>".equals(name) ||
                    (access & Opcodes.ACC_SYNTHETIC) != 0) {
                return mv;
            }

            return new ControllerMethodVisitor(mv, className, name, access);
        }
    }

    public static boolean opcodesIsReturn(int opcode) {
        return opcode == Opcodes.RETURN || opcode == Opcodes.IRETURN ||
               opcode == Opcodes.LRETURN || opcode == Opcodes.FRETURN ||
               opcode == Opcodes.DRETURN || opcode == Opcodes.ARETURN;
    }

    private static class ControllerMethodVisitor extends MethodVisitor {
        private final String className;
        private final String methodName;
        private boolean hasMappingAnnotation = false;
        private boolean codeInjected = false;

        public ControllerMethodVisitor(MethodVisitor mv, String className, String methodName, int access) {
            super(Opcodes.ASM9, mv);
            this.className = className;
            this.methodName = methodName;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            // Check for Spring mapping annotations
            if ("Lorg/springframework/web/bind/annotation/GetMapping;".equals(descriptor) ||
                    "Lorg/springframework/web/bind/annotation/PostMapping;".equals(descriptor) ||
                    "Lorg/springframework/web/bind/annotation/PutMapping;".equals(descriptor) ||
                    "Lorg/springframework/web/bind/annotation/DeleteMapping;".equals(descriptor) ||
                    "Lorg/springframework/web/bind/annotation/PatchMapping;".equals(descriptor) ||
                    "Lorg/springframework/web/bind/annotation/RequestMapping;".equals(descriptor)) {
                hasMappingAnnotation = true;
            }
            return super.visitAnnotation(descriptor, visible);
        }

        @Override
        public void visitCode() {
            codeInjected = true;
            String dotClassName = className.replace('/', '.');
            mv.visitCode();

            // Only inject startRequestFromController if method has mapping annotation
            if (hasMappingAnnotation) {
                
                // Call startRequestFromController() which gets requestId and headers from RequestContextHolder
                mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                        TRACKING_BRIDGE_CLASS,
                        "startRequestFromController",
                        "()V",
                        false);
            }

            // Always inject method tracking code (for all methods in controller)
            mv.visitLdcInsn(dotClassName);
            mv.visitLdcInsn(methodName);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                    TRACKING_BRIDGE_CLASS,
                    "getCurrentDepth",
                    "()I",
                    false);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                    TRACKING_BRIDGE_CLASS,
                    "recordMethodCall",
                    "(Ljava/lang/String;Ljava/lang/String;I)V",
                    false);

            // Increment depth
            mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                    TRACKING_BRIDGE_CLASS,
                    "incrementDepth",
                    "()V",
                    false);
        }

        @Override
        public void visitInsn(int opcode) {
            // Before any return or exception throw, call endRequest if this method has mapping annotation
            if (hasMappingAnnotation && (MethodTrackingTransformer.opcodesIsReturn(opcode) ||
                    opcode == Opcodes.ATHROW)) {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                        TRACKING_BRIDGE_CLASS,
                        "endRequest",
                        "()V",
                        false);
            }

            // Decrement depth before return (for all methods)
            if (MethodTrackingTransformer.opcodesIsReturn(opcode)) {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                        TRACKING_BRIDGE_CLASS,
                        "decrementDepth",
                        "()V",
                        false);
            }
            mv.visitInsn(opcode);
        }

        @Override
        public void visitEnd() {
            super.visitEnd();
        }
    }

    // Special visitor for RepositoryMethodInvoker class
    private static class RepositoryMethodInvokerTrackingClassVisitor extends ClassVisitor {
        private final String className;

        public RepositoryMethodInvokerTrackingClassVisitor(ClassVisitor cv, String className) {
            super(Opcodes.ASM9, cv);
            this.className = className;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            MethodVisitor mv = cv.visitMethod(access, name, descriptor, signature, exceptions);

            // Intercept doInvoke method
            // Method signature: private Object doInvoke(Class<?> repositoryInterface, RepositoryInvocationMulticaster multicaster, Object[] args)
            if ("doInvoke".equals(name)) {
                // Expected signature: (Ljava/lang/Class;Lorg/springframework/data/repository/core/support/RepositoryInvocationMulticaster;[Ljava/lang/Object;)Ljava/lang/Object;
                // But we'll match any method named doInvoke with Class as first parameter
                if (descriptor.contains("Ljava/lang/Class;")) {
                    return new RepositoryMethodInvokerMethodVisitor(mv, className);
                }
            }

            return mv;
        }
    }

    private static class RepositoryMethodInvokerMethodVisitor extends MethodVisitor {

        public RepositoryMethodInvokerMethodVisitor(MethodVisitor mv, String className) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitCode() {
            mv.visitCode();

            // At method entry: get method field and repositoryInterface parameter, then call handleRepositoryMethodInvocation
            // Method signature: doInvoke(Class<?> repositoryInterface, RepositoryInvocationMulticaster multicaster, Object[] args)
            // Load 'this' (index 0)
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            // Get 'method' field from RepositoryMethodInvoker
            // Field name: method, type: java.lang.reflect.Method
            mv.visitFieldInsn(Opcodes.GETFIELD,
                    "org/springframework/data/repository/core/support/RepositoryMethodInvoker",
                    "method",
                    "Ljava/lang/reflect/Method;");

            // Load repositoryInterface parameter (index 1)
            mv.visitVarInsn(Opcodes.ALOAD, 1);

            // Call handleRepositoryMethodInvocation(method, repositoryInterface)
            // This will record the method call but without SQL yet (SQL will be captured later)
            // Note: Using Object type to match the method signature in MethodTrackingBridge
            mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                    TRACKING_BRIDGE_CLASS,
                    "handleRepositoryMethodInvocation",
                    "(Ljava/lang/Object;Ljava/lang/Object;)V",
                    false);

        }

        @Override
        public void visitInsn(int opcode) {
            // Before any return, update the last Repository method call with SQL if available
            if (MethodTrackingTransformer.opcodesIsReturn(opcode)) {

                // Call updateRepositoryMethodCallWithSql() to update the last Repository method call with SQL
                mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                        TRACKING_BRIDGE_CLASS,
                        "updateRepositoryMethodCallWithSql",
                        "()V",
                        false);
            }
            mv.visitInsn(opcode);
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            // Increase maxStack to accommodate our injected code
            int newMaxStack = Math.max(maxStack, 3);
            mv.visitMaxs(newMaxStack, maxLocals);
        }

        @Override
        public void visitEnd() {
            super.visitEnd();
        }
    }

    // Special visitor for Connection class to intercept prepareStatement
    private static class ConnectionTrackingClassVisitor extends ClassVisitor {
        private final String className;

        public ConnectionTrackingClassVisitor(ClassVisitor cv, String className) {
            super(Opcodes.ASM9, cv);
            this.className = className;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            MethodVisitor mv = cv.visitMethod(access, name, descriptor, signature, exceptions);

            // Skip abstract methods and interfaces
            if ((access & Opcodes.ACC_ABSTRACT) != 0) {
                return mv;
            }

            // Intercept prepareStatement(String sql) method
            // Method signature: prepareStatement(String sql) returns PreparedStatement
            if ("prepareStatement".equals(name)) {
                // Match: (Ljava/lang/String;)Ljava/sql/PreparedStatement;
                // Also match other overloads that might take String as first parameter
                if (descriptor.contains("(Ljava/lang/String;")) {
                    return new ConnectionMethodVisitor(mv, className, descriptor);
                }
            }

            return mv;
        }
    }

    private static class ConnectionMethodVisitor extends MethodVisitor {

        public ConnectionMethodVisitor(MethodVisitor mv, String className, String methodDescriptor) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitCode() {
            mv.visitCode();

            // At method entry: get sql parameter and call captureSqlStatement
            // Method signature could be:
            // - prepareStatement(String sql) - sql is at index 1
            // - prepareStatement(String sql, int resultSetType, int resultSetConcurrency) - sql is at index 1
            // - prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) - sql is at index 1
            // - prepareStatement(String sql, int[] columnIndexes) - sql is at index 1
            // - prepareStatement(String sql, String[] columnNames) - sql is at index 1
            // In all cases, sql is the first parameter (index 1, index 0 is 'this')

            // Load sql parameter (index 1, index 0 is 'this')
            mv.visitVarInsn(Opcodes.ALOAD, 1);

            // Call captureSqlStatement(String sql)
            mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                    TRACKING_BRIDGE_CLASS,
                    "captureSqlStatement",
                    "(Ljava/lang/String;)V",
                    false);

        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            // Increase maxStack to accommodate our injected code
            int newMaxStack = Math.max(maxStack, 2);
            mv.visitMaxs(newMaxStack, maxLocals);
        }

        @Override
        public void visitEnd() {
            super.visitEnd();
        }
    }

    // Special visitor for MyBatis SimpleExecutor class
    private static class MyBatisExecutorTrackingClassVisitor extends ClassVisitor {
        private final String className;

        public MyBatisExecutorTrackingClassVisitor(ClassVisitor cv, String className) {
            super(Opcodes.ASM9, cv);
            this.className = className;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            MethodVisitor mv = cv.visitMethod(access, name, descriptor, signature, exceptions);

            // Intercept doUpdate method
            if ("doUpdate".equals(name) &&
                "(Lorg/apache/ibatis/mapping/MappedStatement;Ljava/lang/Object;)I".equals(descriptor)) {
                log.debug("Intercepting MyBatis doUpdate method");
                return new MyBatisUpdateMethodVisitor(mv, className);
            }

            // Intercept doQuery method
            if ("doQuery".equals(name) &&
                descriptor.contains("Lorg/apache/ibatis/mapping/MappedStatement;")) {
                log.debug("Intercepting MyBatis doQuery method");
                return new MyBatisQueryMethodVisitor(mv, className);
            }

            return mv;
        }
    }

    private static class MyBatisUpdateMethodVisitor extends MethodVisitor {
        private boolean codeInjected = false;

        public MyBatisUpdateMethodVisitor(MethodVisitor mv, String className) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitCode() {
            codeInjected = true;
            mv.visitCode();

            // At method entry: record MyBatis mapper method
            // Load MappedStatement parameter (index 1)
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            // Load parameter object (index 2)
            mv.visitVarInsn(Opcodes.ALOAD, 2);

            // Call recordMyBatisMethodEntry(ms, parameter)
            mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                    TRACKING_BRIDGE_CLASS,
                    "recordMyBatisMethodEntry",
                    "(Ljava/lang/Object;Ljava/lang/Object;)V",
                    false);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name,
                                   String descriptor, boolean isInterface) {
            
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);

            // Detect Statement creation and extract SQL immediately
            // Try multiple patterns to ensure we catch the right method
            boolean shouldIntercept = false;
            
            // Pattern 1: prepareStatement method
            if ("prepareStatement".equals(name) &&
                (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKESPECIAL)) {
                shouldIntercept = true;
                log.info("Matched prepareStatement - injecting extractAndRecordMyBatisSql");
            }
            
            // Pattern 2: StatementHandler.prepare method (most likely)
            if ("prepare".equals(name) &&
                descriptor != null && 
                descriptor.contains("Statement")) {
                shouldIntercept = true;
                log.info("Matched prepare - injecting extractAndRecordMyBatisSql");
            }
            
            if (shouldIntercept) {
                // Stack top now has the Statement
                mv.visitInsn(Opcodes.DUP);  // Duplicate Statement for our use
                mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                        TRACKING_BRIDGE_CLASS,
                        "extractAndRecordMyBatisSql",
                        "(Ljava/lang/Object;)V",
                        false);
            }
        }

        @Override
        public void visitInsn(int opcode) {
            // No need to update SQL before return - already done in visitMethodInsn
            super.visitInsn(opcode);
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            // Increase maxStack to accommodate our injected code
            int newMaxStack = Math.max(maxStack, 3);
            super.visitMaxs(newMaxStack, maxLocals);
        }

        @Override
        public void visitEnd() {
            super.visitEnd();
        }
    }

    private static class MyBatisQueryMethodVisitor extends MethodVisitor {
        private boolean codeInjected = false;

        public MyBatisQueryMethodVisitor(MethodVisitor mv, String className) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitCode() {
            codeInjected = true;
            mv.visitCode();

            // At method entry: record MyBatis mapper method
            // Load MappedStatement parameter (index 1)
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            // Load parameter object (index 2)
            mv.visitVarInsn(Opcodes.ALOAD, 2);

            // Call recordMyBatisMethodEntry(ms, parameter)
            mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                    TRACKING_BRIDGE_CLASS,
                    "recordMyBatisMethodEntry",
                    "(Ljava/lang/Object;Ljava/lang/Object;)V",
                    false);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name,
                                   String descriptor, boolean isInterface) {
            // Diagnostic logging to understand actual method calls
            if (name.contains("prepare") || name.contains("Statement")) {
                log.info("MyBatis doQuery method call: owner={}, name={}, opcode={}, descriptor={}", 
                         owner, name, opcode, descriptor);
            }
            
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);

            // Detect Statement creation and extract SQL immediately
            // Try multiple patterns to ensure we catch the right method
            boolean shouldIntercept = false;
            
            // Pattern 1: prepareStatement method
            if ("prepareStatement".equals(name) &&
                (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKESPECIAL)) {
                shouldIntercept = true;
                log.info("Matched prepareStatement - injecting extractAndRecordMyBatisSql");
            }
            
            // Pattern 2: StatementHandler.prepare method (most likely)
            if ("prepare".equals(name) &&
                descriptor != null && 
                descriptor.contains("Statement")) {
                shouldIntercept = true;
                log.info("Matched prepare - injecting extractAndRecordMyBatisSql");
            }
            
            if (shouldIntercept) {
                // Stack top now has the Statement
                mv.visitInsn(Opcodes.DUP);  // Duplicate Statement for our use
                mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                        TRACKING_BRIDGE_CLASS,
                        "extractAndRecordMyBatisSql",
                        "(Ljava/lang/Object;)V",
                        false);
            }
        }

        @Override
        public void visitInsn(int opcode) {
            // No need to update SQL before return - already done in visitMethodInsn
            super.visitInsn(opcode);
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            // Increase maxStack to accommodate our injected code
            int newMaxStack = Math.max(maxStack, 3);
            super.visitMaxs(newMaxStack, maxLocals);
        }

        @Override
        public void visitEnd() {
            super.visitEnd();
        }
    }
}


