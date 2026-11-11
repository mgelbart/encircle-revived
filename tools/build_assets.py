#!/usr/bin/env python3
"""Copy the assets the remake needs out of encircle-old/ into assets/.

Run once from the repo root:  python3 tools/build_assets.py

Produces:
  assets/clusters.json      outline polygon + cell-count likelihoods per cluster image
  assets/clusters/*.png     the cluster images (fruit fly cells)
  assets/sound/*.wav        the four sound effects the 2011 client used
  assets/bg.jpg             the in-game background image
"""
import json, os, shutil, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OLD = os.path.join(ROOT, "encircle-old")
SEG = os.path.join(OLD, "segmentation")
OUT = os.path.join(ROOT, "assets")

def main():
    os.makedirs(os.path.join(OUT, "clusters"), exist_ok=True)
    os.makedirs(os.path.join(OUT, "sound"), exist_ok=True)

    names = [l.strip() for l in open(os.path.join(SEG, "meta", "fly.txt")) if l.strip()]
    clusters = []
    for name in names:
        with open(os.path.join(SEG, "polygons", "fly", name + ".txt")) as f:
            lines = [l.strip() for l in f]
        w, h = (int(v) for v in lines[0].split(","))
        n = int(lines[1])
        xs = [float(v) for v in lines[2].split(",") if v]
        ys = [float(v) for v in lines[3].split(",") if v]
        assert len(xs) == n and len(ys) == n, name
        with open(os.path.join(SEG, "segment_likelihoods", "fly", name + ".txt")) as f:
            lk = {}
            for pair in f.readline().strip().split(","):
                if pair:
                    k, v = pair.split(":")
                    lk[int(k)] = float(v)
        clusters.append({"name": name, "w": w, "h": h, "xs": xs, "ys": ys, "likelihoods": lk})
        shutil.copyfile(os.path.join(SEG, "images", "fly", name + ".png"),
                        os.path.join(OUT, "clusters", name + ".png"))

    with open(os.path.join(OUT, "clusters.json"), "w") as f:
        json.dump(clusters, f, separators=(",", ":"))

    sounds = {
        "shot.wav": "Bow_Fire_Arrow-Stephan_Schutze-2133929391.wav",
        "score.wav": "Metroid_Door-Brandino480-995195341.wav",
        "win.wav": "Electrical_Sweep-Sweeper-1760111493.wav",
        "hit.wav": "36847__EcoDTR__LaserRocket2.wav",
    }
    for dst, src in sounds.items():
        shutil.copyfile(os.path.join(OLD, "sound", src), os.path.join(OUT, "sound", dst))
    shutil.copyfile(os.path.join(OLD, "guiimg", "324740main_tripletgalaxy_600.jpg"),
                    os.path.join(OUT, "bg.jpg"))
    print(f"wrote {len(clusters)} clusters to {OUT}")

if __name__ == "__main__":
    sys.exit(main())
