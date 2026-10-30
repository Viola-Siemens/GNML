package com.hexagram2021.gnml;

import javax.annotation.Nullable;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.hexagram2021.gnml.launch.HmclLocator;
import com.hexagram2021.gnml.launch.HmclProcessLauncher;
import com.hexagram2021.gnml.launch.JavaRuntimeLocator;
import com.hexagram2021.gnml.notify.UserNotifier;
import com.hexagram2021.gnml.transform.AccountPolicyTransformer;
import com.hexagram2021.gnml.transform.IsChinaMainlandTransformer;
import com.hexagram2021.gnml.util.GnmlConstants;
import com.hexagram2021.gnml.util.GnmlLog;

/**
 * Good Night Minecraft Launcher 唯一入口喵~
 * <br/>
 * {@link #main(String[])}：扫描并拉起 HMCL（以本制品为 javaagent）；
 * {@link #premain(String, Instrumentation)}：向 HMCL 的 JVM 注册字节码转换器，
 * 强制 {@code LocaleUtils#isChinaMainland()} 返回 false 喵~
 *
 * @author liudongyu
 */
public final class GoodNightMinecraftLauncher {
	/**
	 * 引导入口：发现 HMCL 并以 javaagent 方式拉起喵~
	 *
	 * @param args 不接受任何参数；收到参数时打印用法并以退出码 1 结束
	 */
	public static void main(String[] args) {
		if (args.length > 0) {
			GnmlLog.error("GNML 不支持命令行参数；请直接运行 java -jar GNML-x.x.x.jar");
			System.exit(GnmlConstants.EXIT_CODE_ERROR);
			return;
		}
		try {
			Path agentJar = resolveAgentJar();
			if (agentJar == null) {
				GnmlLog.error("无法定位 GNML 制品自身 jar（-javaagent 需要绝对路径），请改用 java -jar 方式运行");
				System.exit(GnmlConstants.EXIT_CODE_ERROR);
				return;
			}
			GnmlLog.info("GNML v" + readImplementationVersion() + "，制品: " + agentJar);

			// 1. 两级目录扫描（user.dir 优先，GNML 所在目录兜底）
			Path workDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
			Path fallbackDir = agentJar.getParent();
			List<Path> scannedDirs = new ArrayList<>();
			Optional<Path> hmcl = HmclLocator.locate(workDir, fallbackDir, scannedDirs);
			if (!hmcl.isPresent()) {
				UserNotifier.notifyHmclNotFound(scannedDirs);
				System.exit(GnmlConstants.EXIT_CODE_HMCL_NOT_FOUND);
				return;
			}

			// 2. 解析 java（当前 JVM -> HMCL_JAVA_HOME -> JAVA_HOME -> PATH）
			JavaRuntimeLocator.Resolution java = JavaRuntimeLocator.locate();
			GnmlLog.info("使用 java: " + java);

			// 3. 拉起 HMCL 并透传退出码
			int exitCode = HmclProcessLauncher.launch(java.getCommand(), hmcl.get(), agentJar, workDir);
			GnmlLog.info("HMCL 已退出，退出码: " + exitCode);
			System.exit(exitCode);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			GnmlLog.error("等待 HMCL 退出时被中断", e);
			System.exit(GnmlConstants.EXIT_CODE_ERROR);
		} catch (IOException e) {
			GnmlLog.error("启动 HMCL 子进程失败", e);
			System.exit(GnmlConstants.EXIT_CODE_ERROR);
		}
	}

	/**
	 * JavaAgent 入口：仅注册字节码转换器，不做扫描与 UI 喵~
	 * <br/>
	 * premain 先于 HMCL 任何类加载执行，因此无需 retransform 喵~
	 *
	 * @param agentArgs agent 参数，当前版本忽略
	 * @param inst      Instrumentation 实例
	 */
	public static void premain(String agentArgs, Instrumentation inst) {
		// 不启用 retransform：premain 先于目标类加载，无需对已加载类做二次转换
		inst.addTransformer(new IsChinaMainlandTransformer());
		inst.addTransformer(new AccountPolicyTransformer());
		GnmlLog.info("GNML agent 已挂载：HMCL 的中国大陆判定（LocaleUtils / RegionUtils）将被强制返回 false");
		GnmlLog.info("GNML 账户策略已挂载：旧账户隐藏，新增账户标记，并要求先添加微软账户");
	}

	/**
	 * 定位 GNML 制品自身 jar 的绝对路径喵~
	 *
	 * @return 制品 jar 路径；从 classes 目录（非 jar）运行或位置不可解析时返回 null 喵~
	 */
	@Nullable
	private static Path resolveAgentJar() {
		try {
			URL location = GoodNightMinecraftLauncher.class.getProtectionDomain().getCodeSource().getLocation();
			if (location == null) {
				return null;
			}
			Path path = Paths.get(location.toURI()).toAbsolutePath();
			return Files.isRegularFile(path) ? path : null;
		} catch (Exception e) {
			GnmlLog.warn("定位 GNML 制品路径失败", e);
			return null;
		}
	}

	private static String readImplementationVersion() {
		String version = GoodNightMinecraftLauncher.class.getPackage().getImplementationVersion();
		return version == null ? "development" : version;
	}

	private GoodNightMinecraftLauncher() {
	}
}
