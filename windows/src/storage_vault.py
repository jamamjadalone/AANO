# AANO Windows Desktop App - DPAPI Secure Vault Storage
# Copyright (C) 2026 Jam Amjad Rasheed
# Licensed under GPL-3.0-or-later.

import os
import ctypes
from ctypes import wintypes

class _DATA_BLOB(ctypes.Structure):
    _fields_ = [("cbData", wintypes.DWORD), ("pbData", ctypes.POINTER(ctypes.c_char))]

VAULT_HEADER = b"AANO_VAULT_DPAPI_V1\n"

def _dpapi_encrypt(data: bytes, descr: str = "AANO Secure Vault") -> bytes:
    """Encrypts plaintext bytes using Windows built-in DPAPI."""
    if os.name != "nt" or not hasattr(ctypes, "windll") or not hasattr(ctypes.windll, "crypt32"):
        raise OSError("DPAPI is only supported on Windows NT platforms")
    in_blob = _DATA_BLOB(len(data), ctypes.cast(ctypes.c_char_p(data), ctypes.POINTER(ctypes.c_char)))
    out_blob = _DATA_BLOB()
    if not ctypes.windll.crypt32.CryptProtectData(
        ctypes.byref(in_blob), descr, None, None, None, 0, ctypes.byref(out_blob)
    ):
        raise ctypes.WinError()
    try:
        return ctypes.string_at(out_blob.pbData, out_blob.cbData)
    finally:
        ctypes.windll.kernel32.LocalFree(out_blob.pbData)

def _dpapi_decrypt(encrypted: bytes) -> bytes:
    """Decrypts DPAPI-encrypted bytes for the current logged-in Windows user."""
    if os.name != "nt" or not hasattr(ctypes, "windll") or not hasattr(ctypes.windll, "crypt32"):
        raise OSError("DPAPI is only supported on Windows NT platforms")
    in_blob = _DATA_BLOB(len(encrypted), ctypes.cast(ctypes.c_char_p(encrypted), ctypes.POINTER(ctypes.c_char)))
    out_blob = _DATA_BLOB()
    if not ctypes.windll.crypt32.CryptUnprotectData(
        ctypes.byref(in_blob), None, None, None, None, 0, ctypes.byref(out_blob)
    ):
        raise ctypes.WinError()
    try:
        return ctypes.string_at(out_blob.pbData, out_blob.cbData)
    finally:
        ctypes.windll.kernel32.LocalFree(out_blob.pbData)
