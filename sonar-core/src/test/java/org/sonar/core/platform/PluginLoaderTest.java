/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.core.platform;

import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.assertj.core.data.MapEntry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.Plugin;
import org.sonar.api.SonarPlugin;
import org.sonar.api.utils.ZipUtils;
import org.sonar.updatecenter.common.Version;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PluginLoaderTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  PluginClassloaderFactory classloaderFactory = mock(PluginClassloaderFactory.class);
  PluginLoader loader = new PluginLoader(new FakePluginExploder(), classloaderFactory);

  @Test
  public void instantiate_plugin_entry_point() {
    PluginClassloaderDef def = new PluginClassloaderDef("fake");
    def.addMainClass("fake", FakePlugin.class.getName());

    Map<String, Plugin> instances = loader.instantiatePluginClasses(ImmutableMap.of(def, getClass().getClassLoader()));
    assertThat(instances).containsOnlyKeys("fake");
    assertThat(instances.get("fake")).isInstanceOf(FakePlugin.class);
  }

  @Test
  public void plugin_entry_point_must_be_no_arg_public() {
    PluginClassloaderDef def = new PluginClassloaderDef("fake");
    def.addMainClass("fake", IncorrectPlugin.class.getName());

    try {
      loader.instantiatePluginClasses(ImmutableMap.of(def, getClass().getClassLoader()));
      fail();
    } catch (IllegalStateException e) {
      assertThat(e).hasMessage("Fail to instantiate class [org.sonar.core.platform.PluginLoaderTest$IncorrectPlugin] of plugin [fake]");
    }
  }

  @Test
  public void define_classloader() throws Exception {
    File jarFile = temp.newFile();
    PluginInfo info = new PluginInfo("foo")
      .setJarFile(jarFile)
      .setMainClass("org.foo.FooPlugin")
      .setMinimalSqVersion(Version.create("5.2"));

    Collection<PluginClassloaderDef> defs = loader.defineClassloaders(ImmutableMap.of("foo", info));

    assertThat(defs).hasSize(1);
    PluginClassloaderDef def = defs.iterator().next();
    assertThat(def.getBasePluginKey()).isEqualTo("foo");
    assertThat(def.isSelfFirstStrategy()).isFalse();
    assertThat(def.getFiles()).containsOnly(jarFile);
    assertThat(def.getMainClassesByPluginKey()).containsOnly(MapEntry.entry("foo", "org.foo.FooPlugin"));
    // TODO test mask - require change in sonar-classloader

    // built with SQ 5.2+ -> does not need API compatibility mode
    assertThat(def.isCompatibilityMode()).isFalse();
  }

  @Test
  public void enable_compatibility_mode_if_plugin_is_built_before_5_2() throws Exception {
    File jarFile = temp.newFile();
    PluginInfo info = new PluginInfo("foo")
      .setJarFile(jarFile)
      .setMainClass("org.foo.FooPlugin")
      .setMinimalSqVersion(Version.create("4.5.2"));

    Collection<PluginClassloaderDef> defs = loader.defineClassloaders(ImmutableMap.of("foo", info));
    assertThat(defs.iterator().next().isCompatibilityMode()).isTrue();
  }

  /**
   * A plugin (the "base" plugin) can be extended by other plugins. In this case they share the same classloader.
   */
  @Test
  public void test_plugins_sharing_the_same_classloader() throws Exception {
    File baseJarFile = temp.newFile(), extensionJar1 = temp.newFile(), extensionJar2 = temp.newFile();
    PluginInfo base = new PluginInfo("foo")
      .setJarFile(baseJarFile)
      .setMainClass("org.foo.FooPlugin")
      .setUseChildFirstClassLoader(false);

    PluginInfo extension1 = new PluginInfo("fooExtension1")
      .setJarFile(extensionJar1)
      .setMainClass("org.foo.Extension1Plugin")
      .setBasePlugin("foo");

    // This extension tries to change the classloader-ordering strategy of base plugin
    // (see setUseChildFirstClassLoader(true)).
    // That is not allowed and should be ignored -> strategy is still the one
    // defined on base plugin (parent-first in this example)
    PluginInfo extension2 = new PluginInfo("fooExtension2")
      .setJarFile(extensionJar2)
      .setMainClass("org.foo.Extension2Plugin")
      .setBasePlugin("foo")
      .setUseChildFirstClassLoader(true);

    Collection<PluginClassloaderDef> defs = loader.defineClassloaders(ImmutableMap.of(
      base.getKey(), base, extension1.getKey(), extension1, extension2.getKey(), extension2));

    assertThat(defs).hasSize(1);
    PluginClassloaderDef def = defs.iterator().next();
    assertThat(def.getBasePluginKey()).isEqualTo("foo");
    assertThat(def.isSelfFirstStrategy()).isFalse();
    assertThat(def.getFiles()).containsOnly(baseJarFile, extensionJar1, extensionJar2);
    assertThat(def.getMainClassesByPluginKey()).containsOnly(
      entry("foo", "org.foo.FooPlugin"),
      entry("fooExtension1", "org.foo.Extension1Plugin"),
      entry("fooExtension2", "org.foo.Extension2Plugin"));
    // TODO test mask - require change in sonar-classloader
  }



  /**
   * Does not unzip jar file. It directly returns the JAR file defined on PluginInfo.
   */
  private static class FakePluginExploder extends PluginJarExploder {
    @Override
    public ExplodedPlugin explode(PluginInfo info) {
      return new ExplodedPlugin(info.getKey(), info.getNonNullJarFile(), Collections.<File>emptyList());
    }
  }

  public static class FakePlugin extends SonarPlugin {
    @Override
    public List getExtensions() {
      return Collections.emptyList();
    }
  }

  /**
   * No public empty-param constructor
   */
  public static class IncorrectPlugin extends SonarPlugin {
    public IncorrectPlugin(String s) {
    }

    @Override
    public List getExtensions() {
      return Collections.emptyList();
    }
  }
}
