/*
 * Copyright 2016 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.loader.game;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.entrypoint.EntrypointTransformer;
import net.fabricmc.loader.entrypoint.minecraft.EntrypointPatchBranding;
import net.fabricmc.loader.entrypoint.minecraft.EntrypointPatchFML125;
import net.fabricmc.loader.entrypoint.minecraft.EntrypointPatchHook;
import net.fabricmc.loader.launch.common.FabricLauncherBase;
import net.fabricmc.loader.metadata.BuiltinModMetadata;
import net.fabricmc.loader.util.Arguments;
import net.fabricmc.loader.util.SystemProperties;

import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;

public class MindustryGameProvider implements GameProvider {
	private EnvType envType;
	private String entrypoint;
	private Arguments arguments;
	private Path gameJar;
	private String version;

	public static final EntrypointTransformer TRANSFORMER = new EntrypointTransformer(it -> Arrays.asList(
			new EntrypointPatchHook(it),
			new EntrypointPatchBranding(it),
			new EntrypointPatchFML125(it)
			));

	@Override
	public String getGameId() {
		return "mindustry";
	}

	@Override
	public String getGameName() {
		return "Mindustry";
	}

	@Override
	public String getRawGameVersion() {
		return version;
	}

	@Override
	public String getNormalizedGameVersion() {
		return version;
	}

	@Override
	public Collection<BuiltinMod> getBuiltinMods() {
		URL url;

		try {
			url = gameJar.toUri().toURL();
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}

		return Collections.singletonList(
				new BuiltinMod(url, new BuiltinModMetadata.Builder(getGameId(), getNormalizedGameVersion())
						.setName(getGameName())
						.build())
		);
	}

	public Path getGameJar() {
		return gameJar;
	}

	@Override
	public String getEntrypoint() {
		return entrypoint;
	}

	@Override
	public Path getLaunchDirectory() {
		if (arguments == null) {
			return new File(".").toPath();
		}

		return FabricLauncherBase.getLaunchDirectory(arguments).toPath();
	}

	@Override
	public boolean isObfuscated() {
		return false;
	}

	@Override
	public boolean requiresUrlClassLoader() {
		return false;
	}

	@Override
	public List<Path> getGameContextJars() {
		return Collections.singletonList(gameJar);
	}

	@Override
	public boolean locateGame(EnvType envType, String[] args, ClassLoader loader) {
		this.envType = envType;
		this.arguments = new Arguments();
		arguments.parse(args);

		String clazz;

		if (envType == EnvType.CLIENT) {
			clazz = "mindustry.desktop.DesktopLauncher";
		} else {
			clazz = "mindustry.server.ServerLauncher";
		}

		Optional<GameProviderHelper.EntrypointResult> entrypointResult = GameProviderHelper.findFirstClass(loader, Collections.singletonList(clazz));

		if (!entrypointResult.isPresent()) {
			return false;
		}

		entrypoint = entrypointResult.get().entrypointName;
		gameJar = entrypointResult.get().entrypointPath;

		this.version = arguments.remove(Arguments.GAME_VERSION);
		if (version == null) this.version = System.getProperty(SystemProperties.GAME_VERSION);
		if (version == null) throw new RuntimeException();
		return true;
	}

	@Override
	public String[] getLaunchArguments(boolean sanitize) {
		if (arguments != null) {
			List<String> list = new ArrayList<>(Arrays.asList(arguments.toArray()));

			if (sanitize) {
				int remove = 0;
				Iterator<String> iterator = list.iterator();

				while (iterator.hasNext()) {
					String next = iterator.next();

					if ("--accessToken".equals(next)) {
						remove = 2;
					}

					if (remove > 0) {
						iterator.remove();
						remove--;
					}
				}
			}

			return list.toArray(new String[0]);
		}

		return new String[0];
	}

	@Override
	public EntrypointTransformer getEntrypointTransformer() {
		return TRANSFORMER;
	}

	@Override
	public boolean canOpenErrorGui() {
		// Disabled on macs due to -XstartOnFirstThread being incompatible with awt but required for lwjgl
		if (System.getProperty("os.name").equals("Mac OS X")) {
			return false;
		}

		if (arguments == null || envType == EnvType.CLIENT) {
			return true;
		}

		List<String> extras = arguments.getExtraArgs();
		return !extras.contains("nogui") && !extras.contains("--nogui");
	}

	@Override
	public void launch(ClassLoader loader) {
		try {
			Class<?> c = loader.loadClass(entrypoint);
			Method m = c.getMethod("main", String[].class);
			m.invoke(null, (Object) arguments.toArray());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
