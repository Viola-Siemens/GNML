package com.hexagram2021.gnml.launch;

import javax.annotation.Nullable;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import com.hexagram2021.gnml.util.GnmlConstants;
import com.hexagram2021.gnml.util.GnmlLog;

/**
 * HMCL 启动器定位器：扫描目录中的 {@code *.jar} / {@code *.exe}，
 * 通过 Manifest 的 {@code Main-Class} 判定 HMCL，并按 DR-D4 规则选出最优候选喵~
 *
 * @author liudongyu
 */
public final class HmclLocator {
	/**
	 * 候选优先级比较器：越大越优先喵~
	 * <br/>
	 * 排序规则（DR-D4）：版本号高者优先；同版本 jar 优先于 exe；
	 * 版本均无法解析时按文件名字典序降序喵~
	 */
	static final Comparator<HmclCandidate> PREFERENCE_COMPARATOR = (left, right) -> {
		HmclVersion leftVersion = left.getVersion();
		HmclVersion rightVersion = right.getVersion();
		if (leftVersion == null && rightVersion == null) {
			// 均无法解析版本：文件名字典序大者优先
			return left.getFile().getFileName().toString()
					.compareTo(right.getFile().getFileName().toString());
		}
		if (leftVersion == null) {
			return -1;
		}
		if (rightVersion == null) {
			return 1;
		}
		int versionOrder = leftVersion.compareTo(rightVersion);
		if (versionOrder != 0) {
			return versionOrder;
		}
		// 同版本：jar 优先
		return Boolean.compare(left.isJar(), right.isJar());
	};

	/**
	 * 按两级目录顺序定位 HMCL 喵~
	 *
	 * @param primaryDir  优先扫描的目录（JVM 工作目录）
	 * @param fallbackDir 兜底扫描的目录（GNML 制品所在目录），可为 null 或与 primaryDir 相同
	 * @return 命中的 HMCL 文件；两级目录均未命中时返回空 喵~
	 */
	public static Optional<Path> locate(Path primaryDir, @Nullable Path fallbackDir) {
		return locate(primaryDir, fallbackDir, new ArrayList<>());
	}

	/**
	 * 按两级目录顺序定位 HMCL 喵~
	 *
	 * @param primaryDir   优先扫描的目录（JVM 工作目录）
	 * @param fallbackDir  兜底扫描的目录（GNML 制品所在目录），可为 null 或与 primaryDir 相同
	 * @param scannedDirs  输出参数：按扫描顺序记录实际扫描过的目录，供未命中时提示用户
	 * @return 命中的 HMCL 文件；两级目录均未命中时返回空 喵~
	 */
	public static Optional<Path> locate(Path primaryDir, @Nullable Path fallbackDir, List<Path> scannedDirs) {
		Optional<Path> result = locateIn(primaryDir, scannedDirs);
		if (result.isPresent()) {
			return result;
		}
		if (fallbackDir != null && !fallbackDir.equals(primaryDir)) {
			result = locateIn(fallbackDir, scannedDirs);
			if (result.isPresent()) {
				return result;
			}
		}
		GnmlLog.warn("未在以下目录中找到 HMCL 启动器: " + scannedDirs);
		return Optional.empty();
	}

	private static Optional<Path> locateIn(Path dir, List<Path> scannedDirs) {
		if (!Files.isDirectory(dir)) {
			GnmlLog.warn("目录不存在或不是常规目录，跳过: " + dir);
			return Optional.empty();
		}
		scannedDirs.add(dir);
		List<HmclCandidate> candidates = scan(dir);
		if (candidates.isEmpty()) {
			return Optional.empty();
		}
		HmclCandidate best = selectBest(candidates);
		GnmlLog.info("选定 HMCL 启动器: " + best);
		return Optional.of(best.getFile());
	}

	/**
	 * 扫描单个一级目录，收集所有 HMCL 候选喵~
	 *
	 * @param dir 目标目录
	 * @return 候选列表（未排序）；目录不可读时返回空列表 喵~
	 */
	static List<HmclCandidate> scan(Path dir) {
		List<HmclCandidate> candidates = new ArrayList<>();
		try (DirectoryStream<Path> entries = Files.newDirectoryStream(dir)) {
			for (Path entry : entries) {
				if (!Files.isRegularFile(entry) || !hasLauncherExtension(entry)) {
					continue;
				}
				// 单个文件失败不中断整体扫描
				if (isHmclLauncher(entry)) {
					candidates.add(new HmclCandidate(entry.toAbsolutePath(), HmclVersion.tryParse(entry.getFileName().toString())));
				}
			}
		} catch (IOException e) {
			GnmlLog.warn("扫描目录失败: " + dir, e);
		}
		return candidates;
	}

	/**
	 * 判定单个文件是否为 HMCL 启动器喵~
	 * <br/>
	 * 以 {@code java.util.jar.JarFile} 以只读（不校验签名）方式打开，
	 * Manifest 的 {@code Main-Class} 精确匹配 {@code org.jackhuang.hmcl.Main} 喵~
	 *
	 * @param file 待判定文件
	 * @return 判定结果；文件损坏或不可读时返回 false 喵~
	 */
	static boolean isHmclLauncher(Path file) {
		try (JarFile jarFile = new JarFile(file.toFile(), false)) {
			Manifest manifest = jarFile.getManifest();
			if (manifest == null) {
				return false;
			}
			return GnmlConstants.HMCL_MAIN_CLASS.equals(manifest.getMainAttributes().getValue("Main-Class"));
		} catch (IOException e) {
			// 非 zip / 损坏 / 无权限：不是合法的 HMCL 候选，按预期降级
			GnmlLog.warn("无法作为 zip 读取，跳过: " + file + " (" + e.getMessage() + ")");
			return false;
		}
	}

	/**
	 * 从候选列表中选出最优 HMCL 喵~
	 *
	 * @param candidates 候选列表，非空
	 * @return 最优候选喵~
	 */
	static HmclCandidate selectBest(List<HmclCandidate> candidates) {
		return Collections.max(candidates, PREFERENCE_COMPARATOR);
	}

	private static boolean hasLauncherExtension(Path file) {
		String name = file.getFileName().toString();
		int dot = name.lastIndexOf('.');
		if (dot < 0) {
			return false;
		}
		String extension = name.substring(dot + 1);
		return extension.equalsIgnoreCase("jar") || extension.equalsIgnoreCase("exe") || extension.equalsIgnoreCase("sh");
	}

	private HmclLocator() {
	}
}
