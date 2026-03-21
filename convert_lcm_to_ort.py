#!/usr/bin/env python3
"""
LCM UNet → Android ORT Conversion Script
=========================================
Downloads LCM_Dreamshaper_v7 UNet (ONNX), applies INT8 dynamic quantization,
fixes input shapes to static (512x512, batch=1), and converts to .ort format
for Android NNAPI inference via ONNX Runtime.

Output: unet_lcm_int8.ort (~860 MB)

Prerequisites:
    pip install onnx onnxruntime onnxruntime-tools huggingface_hub

Usage:
    python convert_lcm_to_ort.py
"""

import os
import sys
import shutil
from pathlib import Path

# ── deps check ────────────────────────────────────────────────────────────────
def require(pkg, pip_name=None):
    import importlib
    try:
        importlib.import_module(pkg)
    except ImportError:
        print(f"[ERROR] Missing: {pip_name or pkg}. Run: pip install {pip_name or pkg}")
        sys.exit(1)

require("onnx")
require("onnxruntime")
require("huggingface_hub")

import onnx
import onnxruntime as ort
from onnxruntime.quantization import quantize_dynamic, QuantType
from huggingface_hub import hf_hub_download, snapshot_download

# ── config ────────────────────────────────────────────────────────────────────
REPO_ID      = "SimianLuo/LCM_Dreamshaper_v7"
OUT_DIR      = Path("./lcm_ort_models")
UNET_FIXED   = OUT_DIR / "unet_fixed.onnx"
UNET_QUANT   = OUT_DIR / "unet_lcm_int8.onnx"
UNET_ORT     = OUT_DIR / "unet_lcm_int8.ort"

# Fixed input shapes (512x512 generation, batch=1, no CFG)
BATCH        = 1
SEQ_LEN      = 77
LATENT_C     = 4
LATENT_H     = 64   # 512 / 8
LATENT_W     = 64   # 512 / 8
EMBED_DIM    = 768

OUT_DIR.mkdir(exist_ok=True)

# ── step 1: download ──────────────────────────────────────────────────────────
print("\n[1/4] Downloading LCM UNet ONNX from HuggingFace (~3.4 GB)...")
print("      This will be cached in ~/.cache/huggingface/")

unet_onnx_path   = hf_hub_download(REPO_ID, filename="unet/model.onnx")
unet_data_path   = hf_hub_download(REPO_ID, filename="unet/model.onnx_data")

unet_dir = Path(unet_onnx_path).parent
print(f"      Cached at: {unet_dir}")

# ── step 2: print current shapes (info only) ──────────────────────────────────
print("\n[2/4] Inspecting model inputs...")
model_meta = onnx.load(str(unet_onnx_path), load_external_data=False)
print("      Current input shapes:")
for inp in model_meta.graph.input:
    shape = [
        d.dim_value if d.dim_value else d.dim_param
        for d in inp.type.tensor_type.shape.dim
    ]
    print(f"        {inp.name}: {shape}")
del model_meta

# ── step 3: INT8 dynamic quantization on original model ───────────────────────
# Quantize the original model directly (has external data file alongside it).
# Shape fixing happens AFTER quantization to avoid losing external weights.
print("\n[3/4] Applying INT8 dynamic quantization (this takes ~5-15 min)...")
print("      Quantizing from original model path (preserves external data).")

quantize_dynamic(
    model_input=str(unet_onnx_path),
    model_output=str(UNET_QUANT),
    weight_type=QuantType.QInt8,
)

# Now fix dynamic shapes on the quantized model (weights are embedded, safe to modify)
print("      Fixing dynamic input shapes to static (batch=1, 512x512)...")
STATIC_SHAPES = {
    "sample":               [BATCH, LATENT_C, LATENT_H, LATENT_W],
    "timestep":             [BATCH],
    "encoder_hidden_states":[BATCH, SEQ_LEN, EMBED_DIM],
    "timestep_cond":        [BATCH, 256],
}
qmodel = onnx.load(str(UNET_QUANT))
for inp in qmodel.graph.input:
    static = STATIC_SHAPES.get(inp.name)
    if not static:
        continue
    for i, dim in enumerate(inp.type.tensor_type.shape.dim):
        if i < len(static):
            dim.ClearField("dim_param")
            dim.dim_value = static[i]
onnx.save(qmodel, str(UNET_QUANT))
print("      Shapes fixed.")

size_mb = UNET_QUANT.stat().st_size / 1_048_576
print(f"      Quantized model size: {size_mb:.0f} MB → {UNET_QUANT}")

# ── step 4: convert to .ort ───────────────────────────────────────────────────
print("\n[4/4] Converting to ORT format for Android...")
try:
    from onnxruntime.tools import convert_onnx_models_to_ort
    convert_onnx_models_to_ort.convert_onnx_models_to_ort(
        str(UNET_QUANT.parent),
        output_dir=str(OUT_DIR),
        optimization_style="Fixed",   # Fixed = optimized for static shapes
    )
    # rename output
    ort_candidates = list(OUT_DIR.glob("*.ort"))
    if ort_candidates:
        best = max(ort_candidates, key=lambda p: p.stat().st_size)
        best.rename(UNET_ORT)
        print(f"      ORT model → {UNET_ORT} ({UNET_ORT.stat().st_size / 1_048_576:.0f} MB)")
    else:
        print("      WARNING: no .ort file produced — the quantized .onnx can be used directly with OrtSession on Android")
        shutil.copy(UNET_QUANT, OUT_DIR / "unet_lcm_int8_direct.onnx")

except Exception as e:
    print(f"      ORT conversion failed ({e})")
    print("      The quantized .onnx is still usable directly with OrtSession on Android")

# ── summary ───────────────────────────────────────────────────────────────────
print("\n" + "="*60)
print("DONE. Files for Android:")
print()
for f in sorted(OUT_DIR.glob("*")):
    if f.suffix in (".ort", ".onnx"):
        print(f"  {f.name:45s} {f.stat().st_size / 1_048_576:7.0f} MB")
print()
print("Next steps:")
print("  1. Copy unet_lcm_int8.ort (or .onnx) to the device")
print("  2. Reuse anthrapper text_encoder_quant.ort + decoder_quant.ort as-is")
print("  3. Update ModelRepository URLs to point to these files")
print("="*60)
