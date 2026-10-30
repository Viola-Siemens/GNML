package com.hexagram2021.gnml.transform;

import javax.annotation.Nullable;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * 在 HMCL 账户加载、保存与用户界面入口处应用 GNML 账户标签策略。
 */
public final class AccountPolicyTransformer implements ClassFileTransformer {
	private static final String ACCOUNTS = "org/jackhuang/hmcl/setting/Accounts";
	private static final String ACCOUNT_LIST_PAGE = "org/jackhuang/hmcl/ui/account/AccountListPage";
	private static final String CREATE_ACCOUNT_PANE = "org/jackhuang/hmcl/ui/account/CreateAccountPane";
	private static final String MICROSOFT_LOGIN_PANE = "org/jackhuang/hmcl/ui/account/MicrosoftAccountLoginPane";
	private static final String ACCOUNT_LIST_POPUP = "org/jackhuang/hmcl/ui/account/AccountListPopupMenu";
	private static final String ACCOUNT_ADVANCED_LIST_ITEM = "org/jackhuang/hmcl/ui/account/AccountAdvancedListItem";
	private static final String ACCOUNT_DESC = "Lorg/jackhuang/hmcl/auth/Account;";
	private static final String JSON_OBJECT_DESC = "Lcom/google/gson/JsonObject;";
	private static final String OBSERVABLE_LIST_DESC = "Ljavafx/collections/ObservableList;";
	private static final String POLICY = "com/hexagram2021/gnml/transform/GnmlAccountPolicy";
	private static final String POLICY_OBJECT_METHOD = "(Ljava/lang/Object;)Ljava/lang/Object;";
	private static final String POLICY_TWO_OBJECT_METHOD = "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";
	private static final String POLICY_TWO_OBJECT_VOID_METHOD = "(Ljava/lang/Object;Ljava/lang/Object;)V";

	@Override
	@Nullable
	public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
							ProtectionDomain protectionDomain, byte[] classFileBuffer)
				throws IllegalClassFormatException {
		if (className == null || !isTarget(className)) {
			return null;
		}

		try {
			ClassReader reader = new ClassReader(classFileBuffer);
			ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
			Set<String> transformedMethods = new HashSet<>();
			reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
				@Override
				public MethodVisitor visitMethod(int access, String name, String descriptor,
											String signature, String[] exceptions) {
					MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
					if (ACCOUNTS.equals(className)) {
						if ("parseAccount".equals(name) && descriptor.startsWith("(" + JSON_OBJECT_DESC + "Z)")) {
							transformedMethods.add("parseAccount");
							return new MethodVisitor(Opcodes.ASM9, delegate) {
								@Override
								public void visitInsn(int opcode) {
									if (opcode == Opcodes.ARETURN) {
										mv.visitInsn(Opcodes.DUP);
										mv.visitVarInsn(Opcodes.ALOAD, 0);
										mv.visitMethodInsn(Opcodes.INVOKESTATIC, POLICY, "markLoaded",
												"(Ljava/lang/Object;Ljava/lang/Object;)V", false);
									}
									mv.visitInsn(opcode);
								}
							};
						}
						if ("serializeAccount".equals(name) && descriptor.startsWith("(" + ACCOUNT_DESC + ")")) {
							transformedMethods.add("serializeAccount");
							return new MethodVisitor(Opcodes.ASM9, delegate) {
								@Override
								public void visitInsn(int opcode) {
									if (opcode == Opcodes.ARETURN) {
										mv.visitInsn(Opcodes.DUP);
										mv.visitVarInsn(Opcodes.ALOAD, 0);
										mv.visitMethodInsn(Opcodes.INVOKESTATIC, POLICY, "tagSerialized",
												"(Ljava/lang/Object;Ljava/lang/Object;)V", false);
									}
									mv.visitInsn(opcode);
								}
							};
						}
						if ("getSelectedAccount".equals(name) && ("()" + ACCOUNT_DESC).equals(descriptor)) {
							transformedMethods.add("getSelectedAccount");
							return new MethodVisitor(Opcodes.ASM9, delegate) {
								@Override
								public void visitInsn(int opcode) {
									if (opcode == Opcodes.ARETURN) {
										mv.visitMethodInsn(Opcodes.INVOKESTATIC, POLICY, "visibleOrNull",
												POLICY_OBJECT_METHOD, false);
										mv.visitTypeInsn(Opcodes.CHECKCAST, "org/jackhuang/hmcl/auth/Account");
									}
									mv.visitInsn(opcode);
								}
							};
						}
						if ("setSelectedAccount".equals(name) && ("(" + ACCOUNT_DESC + ")V").equals(descriptor)) {
							transformedMethods.add("setSelectedAccount");
							return new MethodVisitor(Opcodes.ASM9, delegate) {
								@Override
								public void visitCode() {
									super.visitCode();
									mv.visitVarInsn(Opcodes.ALOAD, 0);
									mv.visitMethodInsn(Opcodes.INVOKESTATIC, POLICY, "visibleOrNull",
											POLICY_OBJECT_METHOD, false);
									mv.visitTypeInsn(Opcodes.CHECKCAST, "org/jackhuang/hmcl/auth/Account");
									mv.visitVarInsn(Opcodes.ASTORE, 0);
								}
							};
						}
						if ("init".equals(name) && "()V".equals(descriptor) || name.startsWith("lambda$init$")) {
							if ("init".equals(name)) {
								transformedMethods.add("init");
							}
							return new MethodVisitor(Opcodes.ASM9, delegate) {
								private boolean selectedAccountProperty;

								@Override
								public void visitFieldInsn(int opcode, String owner, String fieldName, String fieldDescriptor) {
									if (opcode == Opcodes.GETSTATIC && ACCOUNTS.equals(owner)
											&& "selectedAccount".equals(fieldName)) {
										selectedAccountProperty = true;
									}
									mv.visitFieldInsn(opcode, owner, fieldName, fieldDescriptor);
									if (opcode == Opcodes.PUTSTATIC && ACCOUNTS.equals(owner)
											&& "initialized".equals(fieldName)) {
										mv.visitMethodInsn(Opcodes.INVOKESTATIC, POLICY, "persistLoadedTags", "()V", false);
									}
								}

								@Override
								public void visitMethodInsn(int opcode, String owner, String methodName,
												   String methodDescriptor, boolean isInterface) {
									if (ACCOUNTS.equals(className) && "logIn".equals(methodName)
											&& "()Lorg/jackhuang/hmcl/auth/AuthInfo;".equals(methodDescriptor)) {
										mv.visitMethodInsn(Opcodes.INVOKESTATIC, POLICY, "loginIfVisible",
												"(Ljava/lang/Object;)Ljava/lang/Object;", false);
										mv.visitTypeInsn(Opcodes.CHECKCAST, "org/jackhuang/hmcl/auth/AuthInfo");
									} else if (selectedAccountProperty && "set".equals(methodName)
											&& "(Ljava/lang/Object;)V".equals(methodDescriptor)) {
										mv.visitMethodInsn(Opcodes.INVOKESTATIC, POLICY, "setSelectedAccountProperty",
												POLICY_TWO_OBJECT_VOID_METHOD, false);
									} else {
										mv.visitMethodInsn(opcode, owner, methodName, methodDescriptor, isInterface);
									}
									selectedAccountProperty = false;
								}
							};
						}
					}
					if (ACCOUNT_LIST_PAGE.equals(className) && "<clinit>".equals(name)) {
						transformedMethods.add("AccountListPage.<clinit>");
						return new MethodVisitor(Opcodes.ASM9, delegate) {
							@Override
							public void visitInsn(int opcode) {
								if (opcode == Opcodes.RETURN) {
									mv.visitMethodInsn(Opcodes.INVOKESTATIC, POLICY, "updateRestriction", "()V", false);
								}
								mv.visitInsn(opcode);
							}
						};
					}
					if (ACCOUNT_LIST_PAGE.equals(className) && "<init>".equals(name)) {
						return new MethodVisitor(Opcodes.ASM9, delegate) {
							@Override
							public void visitMethodInsn(int opcode, String owner, String methodName,
											   String methodDescriptor, boolean isInterface) {
								if ("org/jackhuang/hmcl/util/javafx/MappedObservableList".equals(owner)
										&& "create".equals(methodName)
										&& methodDescriptor.startsWith("(" + OBSERVABLE_LIST_DESC)) {
									transformedMethods.add("AccountListPage.accounts");
									mv.visitMethodInsn(Opcodes.INVOKESTATIC, POLICY, "visibleMappedAccounts",
											POLICY_TWO_OBJECT_METHOD, false);
									mv.visitTypeInsn(Opcodes.CHECKCAST, "javafx/collections/ObservableList");
								} else {
									mv.visitMethodInsn(opcode, owner, methodName, methodDescriptor, isInterface);
								}
							}
						};
					}
					if (CREATE_ACCOUNT_PANE.equals(className) && "<init>".equals(name)
							&& descriptor.startsWith("(Lorg/jackhuang/hmcl/auth/AccountFactory;")) {
						transformedMethods.add("CreateAccountPane.factory");
						return new MethodVisitor(Opcodes.ASM9, delegate) {
							private boolean adjusted;

							@Override
							public void visitMethodInsn(int opcode, String owner, String methodName,
											   String methodDescriptor, boolean isInterface) {
								mv.visitMethodInsn(opcode, owner, methodName, methodDescriptor, isInterface);
								if (!adjusted && opcode == Opcodes.INVOKESPECIAL && "<init>".equals(methodName)
										&& !CREATE_ACCOUNT_PANE.equals(owner)) {
									adjusted = true;
									mv.visitVarInsn(Opcodes.ALOAD, 1);
									mv.visitMethodInsn(Opcodes.INVOKESTATIC, POLICY, "restrictFactory",
											POLICY_OBJECT_METHOD, false);
									mv.visitTypeInsn(Opcodes.CHECKCAST, "org/jackhuang/hmcl/auth/AccountFactory");
									mv.visitVarInsn(Opcodes.ASTORE, 1);
								}
							}
						};
					}
					if ((CREATE_ACCOUNT_PANE.equals(className) || MICROSOFT_LOGIN_PANE.equals(className))
							&& "completeLogin".equals(name)
							&& (descriptor.startsWith("(" + ACCOUNT_DESC)
									|| descriptor.startsWith("(Lorg/jackhuang/hmcl/auth/microsoft/MicrosoftAccount;"))) {
						transformedMethods.add(className + ".completeLogin");
						return new MethodVisitor(Opcodes.ASM9, delegate) {
							@Override
							public void visitCode() {
								super.visitCode();
								mv.visitVarInsn(Opcodes.ALOAD, 1);
								mv.visitMethodInsn(Opcodes.INVOKESTATIC, POLICY, "markAdded",
										"(Ljava/lang/Object;)V", false);
							}
						};
					}
					if (ACCOUNT_LIST_POPUP.equals(className)
							|| ACCOUNT_ADVANCED_LIST_ITEM.equals(className) && "<init>".equals(name)) {
						return new MethodVisitor(Opcodes.ASM9, delegate) {
							@Override
							public void visitMethodInsn(int opcode, String owner, String methodName,
											   String methodDescriptor, boolean isInterface) {
								if (ACCOUNTS.equals(owner) && "getAccounts".equals(methodName)
										&& ("()" + OBSERVABLE_LIST_DESC).equals(methodDescriptor)) {
									mv.visitMethodInsn(opcode, owner, methodName, methodDescriptor, isInterface);
									mv.visitMethodInsn(Opcodes.INVOKESTATIC, POLICY, "visibleAccounts",
											POLICY_OBJECT_METHOD, false);
									mv.visitTypeInsn(Opcodes.CHECKCAST, "javafx/collections/ObservableList");
								} else {
									mv.visitMethodInsn(opcode, owner, methodName, methodDescriptor, isInterface);
								}
							}
						};
					}
					return delegate;
				}
			}, 0);

			if (ACCOUNTS.equals(className)) {
				String[] required = {"parseAccount", "serializeAccount", "getSelectedAccount", "setSelectedAccount", "init"};
				for (String method : required) {
					if (!transformedMethods.contains(method)) {
						throw new IllegalClassFormatException("HMCL Accounts 缺少账户策略目标方法: " + method);
					}
				}
			}
			if (ACCOUNT_LIST_PAGE.equals(className) && !transformedMethods.contains("AccountListPage.accounts")) {
				throw new IllegalClassFormatException("HMCL AccountListPage 缺少账户列表转换入口");
			}
			if (CREATE_ACCOUNT_PANE.equals(className) && (!transformedMethods.contains("CreateAccountPane.factory")
					|| !transformedMethods.contains(CREATE_ACCOUNT_PANE + ".completeLogin"))) {
				throw new IllegalClassFormatException("HMCL CreateAccountPane 缺少账户创建策略入口");
			}
			if (MICROSOFT_LOGIN_PANE.equals(className)
					&& !transformedMethods.contains(MICROSOFT_LOGIN_PANE + ".completeLogin")) {
				throw new IllegalClassFormatException("HMCL MicrosoftAccountLoginPane 缺少账户标记入口");
			}
			return writer.toByteArray();
		} catch (IllegalClassFormatException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalClassFormatException("转换 HMCL 账户类 " + className + " 失败: " + e.getMessage());
		}
	}

	private static boolean isTarget(String className) {
		return ACCOUNTS.equals(className) || ACCOUNT_LIST_PAGE.equals(className)
				|| CREATE_ACCOUNT_PANE.equals(className) || MICROSOFT_LOGIN_PANE.equals(className)
				|| ACCOUNT_LIST_POPUP.equals(className)
				|| ACCOUNT_ADVANCED_LIST_ITEM.equals(className);
	}
}
