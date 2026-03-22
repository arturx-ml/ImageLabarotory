"""Helper script to convert diffusion checkpoints to format used by MediaPipe Image Generator.

Source: https://github.com/googlesamples/mediapipe/blob/main/tools/image_generator_converter/convert.py
"""

import os

from absl import app
from absl import flags
import requests
import torch as th


_CKPT_PATH = flags.DEFINE_string(
    "ckpt_path", default=None, help="Path to checkpoint file", required=True)
_OUTPUT_PATH = flags.DEFINE_string(
    "output_path", default="bins", help="Output folder path", required=False)

VOCAB_URL = "https://openaipublic.blob.core.windows.net/clip/bpe_simple_vocab_16e6.txt"


def run(ckpt_path, output_path):
    """Converts the checkpoint and saves the result.

    Args:
      ckpt_path: Source checkpoint path
      output_path: Result folder directory
    """
    os.makedirs(output_path, exist_ok=True)
    ckpt = th.load(ckpt_path, map_location="cpu", weights_only=False)

    vocab_dest = os.path.join(output_path, os.path.basename(VOCAB_URL))
    if not os.path.exists(vocab_dest):
        print("Downloading CLIP vocabulary...")
        with requests.get(VOCAB_URL, stream=True) as response:
            with open(vocab_dest, "wb") as vocab_file:
                for c in response.iter_content(chunk_size=8192):
                    vocab_file.write(c)
        print("Vocabulary downloaded.")

    print(f"Converting checkpoint: {ckpt_path}")
    count = 0
    for k, v in ckpt["state_dict"].items():
        if "first_stage_model.encoder" in k:
            continue
        if not hasattr(v, "numpy"):
            continue
        output_bin_file = os.path.join(output_path, f"{k}.bin")
        v.numpy().astype("float16").tofile(output_bin_file)
        count += 1

    print(f"Done! Converted {count} tensors to {output_path}/")


def main(_) -> None:
    ckpt_path = _CKPT_PATH.value
    output_path = _OUTPUT_PATH.value
    run(ckpt_path, output_path)


if __name__ == "__main__":
    app.run(main)
