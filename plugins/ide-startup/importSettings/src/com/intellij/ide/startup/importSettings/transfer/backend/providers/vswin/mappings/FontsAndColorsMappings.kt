// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.providers.vswin.mappings

import com.intellij.ide.startup.importSettings.db.KnownLafs
import com.intellij.ide.startup.importSettings.models.BundledLookAndFeel


object FontsAndColorsMappings {
  enum class VsTheme(val themeUuid: String) {
    // Valid for VS2019
    Dark("1DED0138-47CE-435E-84EF-9EC1F439B749"),
    Light("DE3DBBCD-F642-433C-8353-8F1DF4370ABA"),
    Blue("A4D6A176-B948-4B29-8C66-53C97A1ED7D0"),
    BlueExtraContrast("CE94D289-8481-498B-8CA9-9B6191A315B9"),
    HighContrast("A5C004B4-2D4B-494E-BF01-45FC492522C7");

    override fun toString(): String {
      return when (this) {
        Dark -> "dark"
        Light -> "light"
        Blue -> "blue"
        BlueExtraContrast -> "blue extra contrast"
        HighContrast -> "high contrast"
      }
    }

    fun toRiderTheme(): BundledLookAndFeel = when (this) {
      Dark -> KnownLafs.Darcula
      Light -> KnownLafs.Light
      Blue -> KnownLafs.Light
      BlueExtraContrast -> KnownLafs.Light
      HighContrast -> KnownLafs.HighContrast
    }

    companion object {
      fun fromString(value: String): VsTheme = values().firstOrNull { it.themeUuid == value } ?: Dark
    }
  }

}