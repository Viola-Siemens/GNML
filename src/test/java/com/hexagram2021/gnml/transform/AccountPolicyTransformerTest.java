package com.hexagram2021.gnml.transform;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.instrument.IllegalClassFormatException;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class AccountPolicyTransformerTest {
	private static final String ACCOUNTS = "org/jackhuang/hmcl/setting/Accounts";
	private static final String CREATE_ACCOUNT_PANE = "org/jackhuang/hmcl/ui/account/CreateAccountPane";
	private static final String MICROSOFT_LOGIN_PANE = "org/jackhuang/hmcl/ui/account/MicrosoftAccountLoginPane";
	private static final String ACCOUNT = "Lorg/jackhuang/hmcl/auth/Account;";
	private static final String JSON_OBJECT = "Lcom/google/gson/JsonObject;";
	private static final String POLICY = "com/hexagram2021/gnml/transform/GnmlAccountPolicy";

	@Test
	void transformsAccountLoadingSavingAndSelectionHooks() throws Exception {
		byte[] transformed = new AccountPolicyTransformer().transform(
				getClass().getClassLoader(), ACCOUNTS, null, null, accountsClassBytes());
		int[] hookCount = {0};
		new ClassReader(transformed).accept(new ClassVisitor(Opcodes.ASM9) {
			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor,
											 String signature, String[] exceptions) {
				return new MethodVisitor(Opcodes.ASM9) {
					@Override
					public void visitMethodInsn(int opcode, String owner, String methodName,
												String methodDescriptor, boolean isInterface) {
						if (opcode == Opcodes.INVOKESTATIC && POLICY.equals(owner)) {
							hookCount[0]++;
						}
					}
				};
			}
		}, 0);

		assertTrue(hookCount[0] >= 7);
	}

	@Test
	void failsFastWhenAccountStorageMethodsChange() throws Exception {
		ClassWriter writer = new ClassWriter(0);
		writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, ACCOUNTS, null, "java/lang/Object", null);
		addMethod(writer, "parseAccount", "(" + JSON_OBJECT + "Z)" + ACCOUNT, Opcodes.ACONST_NULL, Opcodes.ARETURN);
		addMethod(writer, "serializeAccount", "(" + ACCOUNT + ")Ljava/lang/Object;", Opcodes.ACONST_NULL, Opcodes.ARETURN);
		addMethod(writer, "getSelectedAccount", "()" + ACCOUNT, Opcodes.ACONST_NULL, Opcodes.ARETURN);
		addMethod(writer, "setSelectedAccount", "(" + ACCOUNT + ")V", Opcodes.RETURN);
		writer.visitEnd();

		try {
			new AccountPolicyTransformer().transform(getClass().getClassLoader(), ACCOUNTS, null, null,
					writer.toByteArray());
			throw new AssertionError("Expected an incompatible HMCL account class to fail fast");
		} catch (IllegalClassFormatException expected) {
			assertTrue(expected.getMessage().contains("init"));
		}
	}

	@Test
	void marksAccountsBeforeTheyAreAddedToTheObservableList() throws Exception {
		assertMarkAddedHook(CREATE_ACCOUNT_PANE, "(" + ACCOUNT + ")V");
		assertMarkAddedHook(MICROSOFT_LOGIN_PANE,
				"(Lorg/jackhuang/hmcl/auth/microsoft/MicrosoftAccount;)V");
	}

	private void assertMarkAddedHook(String className, String descriptor) throws Exception {
		ClassWriter writer = new ClassWriter(0);
		writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null);
		if (CREATE_ACCOUNT_PANE.equals(className)) {
			MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
					"(Lorg/jackhuang/hmcl/auth/AccountFactory;)V", null, null);
			constructor.visitCode();
			constructor.visitVarInsn(Opcodes.ALOAD, 0);
			constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
			constructor.visitInsn(Opcodes.RETURN);
			constructor.visitMaxs(1, 2);
			constructor.visitEnd();
		}
		addMethod(writer, "completeLogin", descriptor, Opcodes.RETURN);
		writer.visitEnd();
		byte[] transformed = new AccountPolicyTransformer().transform(
				getClass().getClassLoader(), className, null, null, writer.toByteArray());
		boolean[] found = {false};
		new ClassReader(transformed).accept(new ClassVisitor(Opcodes.ASM9) {
			@Override
			public MethodVisitor visitMethod(int access, String name, String methodDescriptor,
											 String signature, String[] exceptions) {
				return new MethodVisitor(Opcodes.ASM9) {
					@Override
					public void visitMethodInsn(int opcode, String owner, String methodName,
												String methodDescriptor, boolean isInterface) {
						found[0] |= opcode == Opcodes.INVOKESTATIC && POLICY.equals(owner) && "markAdded".equals(methodName);
					}
				};
			}
		}, 0);
		assertTrue(found[0]);
	}

	private static byte[] accountsClassBytes() {
		ClassWriter writer = new ClassWriter(0);
		writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, ACCOUNTS, null, "java/lang/Object", null);
		writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "selectedAccount",
				"Ljavafx/beans/property/ObjectProperty;", null, null).visitEnd();
		writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "initialized", "Z", null, null).visitEnd();
		addMethod(writer, "parseAccount", "(" + JSON_OBJECT + "Z)" + ACCOUNT, Opcodes.ACONST_NULL, Opcodes.ARETURN);
		addMethod(writer, "serializeAccount", "(" + ACCOUNT + ")Ljava/lang/Object;", Opcodes.ACONST_NULL, Opcodes.ARETURN);
		addMethod(writer, "getSelectedAccount", "()" + ACCOUNT, Opcodes.ACONST_NULL, Opcodes.ARETURN);
		addMethod(writer, "setSelectedAccount", "(" + ACCOUNT + ")V", Opcodes.RETURN);
		MethodVisitor init = writer.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "init", "()V", null, null);
		init.visitCode();
		init.visitFieldInsn(Opcodes.GETSTATIC, ACCOUNTS, "selectedAccount", "Ljavafx/beans/property/ObjectProperty;");
		init.visitInsn(Opcodes.ACONST_NULL);
		init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "javafx/beans/property/ObjectProperty", "set",
				"(Ljava/lang/Object;)V", false);
		init.visitInsn(Opcodes.ICONST_1);
		init.visitFieldInsn(Opcodes.PUTSTATIC, ACCOUNTS, "initialized", "Z");
		init.visitInsn(Opcodes.RETURN);
		init.visitMaxs(2, 0);
		init.visitEnd();
		MethodVisitor login = writer.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "lambda$init$0",
				"(" + ACCOUNT + ")V", null, null);
		login.visitCode();
		login.visitVarInsn(Opcodes.ALOAD, 0);
		login.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "org/jackhuang/hmcl/auth/Account", "logIn",
				"()Lorg/jackhuang/hmcl/auth/AuthInfo;", false);
		login.visitInsn(Opcodes.POP);
		login.visitInsn(Opcodes.RETURN);
		login.visitMaxs(1, 1);
		login.visitEnd();
		writer.visitEnd();
		return writer.toByteArray();
	}

	private static void addMethod(ClassWriter writer, String name, String descriptor, int... instructions) {
		MethodVisitor method = writer.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, name, descriptor, null, null);
		method.visitCode();
		for (int instruction : instructions) {
			method.visitInsn(instruction);
		}
		method.visitMaxs(1, 2);
		method.visitEnd();
	}
}