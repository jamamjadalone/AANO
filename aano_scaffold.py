#!/usr/bin/env python3
"""
AANO Scaffolding Module — Generates Rule-21-Compliant Boilerplate Templates
Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later
"""

from pathlib import Path

ACTIVITY_TEMPLATE = """// {name}Activity: Screen for AANO.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.pages

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import app.aa.aano.databinding.Activity{name}Binding

class {name}Activity : AppCompatActivity() {{

    private lateinit var binding: Activity{name}Binding

    override fun onCreate(savedInstanceState: Bundle?) {{
        super.onCreate(savedInstanceState)
        binding = Activity{name}Binding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener {{ finish() }}
    }}
}}
"""

FRAGMENT_TEMPLATE = """// {name}Fragment: UI fragment for AANO.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.pages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import app.aa.aano.databinding.Fragment{name}Binding

class {name}Fragment : Fragment() {{

    private var _binding: Fragment{name}Binding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {{
        _binding = Fragment{name}Binding.inflate(inflater, container, false)
        return binding.root
    }}

    override fun onDestroyView() {{
        super.onDestroyView()
        _binding = null
    }}
}}
"""

HELPER_TEMPLATE = """// {name}Helper: Reusable component delegate for AANO.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.components.editor

import android.content.Context
import android.util.Log

class {name}Helper(private val context: Context) {{

    companion object {{
        private const val TAG = "{name}Helper"
    }}

    fun execute() {{
        Log.d(TAG, "Executing {name} operation")
    }}
}}
"""

DIALOG_TEMPLATE = """// {name}DialogHelper: Reusable modal dialog for AANO.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.components.dialogs

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object {name}DialogHelper {{

    fun show(context: Context, onConfirmed: () -> Unit) {{
        MaterialAlertDialogBuilder(context)
            .setTitle("{name}")
            .setMessage("Configure {name} settings.")
            .setPositiveButton(android.R.string.ok) {{ _, _ -> onConfirmed() }}
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }}
}}
"""


def scaffold(src_root: Path, target_type: str, name: str) -> Path:
    clean_name = name[0].upper() + name[1:]
    if target_type in ("activity", "screen"):
        clean_name = clean_name.removesuffix("Activity")
        target_file = src_root / "pages" / f"{clean_name}Activity.kt"
        content = ACTIVITY_TEMPLATE.format(name=clean_name)
    elif target_type in ("fragment", "page"):
        clean_name = clean_name.removesuffix("Fragment")
        target_file = src_root / "pages" / f"{clean_name}Fragment.kt"
        content = FRAGMENT_TEMPLATE.format(name=clean_name)
    elif target_type in ("helper", "editor_helper"):
        clean_name = clean_name.removesuffix("Helper")
        target_file = src_root / "components" / "editor" / f"{clean_name}Helper.kt"
        content = HELPER_TEMPLATE.format(name=clean_name)
    elif target_type in ("dialog", "sheet"):
        clean_name = clean_name.removesuffix("DialogHelper").removesuffix("Dialog")
        target_file = src_root / "components" / "dialogs" / f"{clean_name}DialogHelper.kt"
        content = DIALOG_TEMPLATE.format(name=clean_name)
    else:
        raise ValueError(f"Unknown scaffold type: {target_type}. Valid: activity, fragment, helper, dialog")

    if target_file.exists():
        raise FileExistsError(f"File already exists: {target_file}")

    target_file.parent.mkdir(parents=True, exist_ok=True)
    target_file.write_text(content.strip() + "\n", encoding="utf-8")
    return target_file
