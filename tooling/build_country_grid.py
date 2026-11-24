"""Build the offline conservative interior grid from pinned Natural Earth 5.1.1.
Build-time dependencies only: numpy and shapely 2. Input is the unmodified
ne_50m_admin_0_countries.geojson from nvkelso/natural-earth-vector tag v5.1.1.
"""
import argparse
import hashlib
import json
from pathlib import Path

import numpy as np
import shapely
from shapely.geometry import shape

def build(source, output):
    raw = source.read_bytes()
    if hashlib.sha256(raw).hexdigest() != "3e458fc036ad0a66411f2c1e6cac49c5d7bfb81cb1123bc513b22511a2b7fdeb":
        raise ValueError("Unexpected boundary dataset")
    # One cell spans 0.2 degrees. Certify the whole cell plus a margin of
    # approximately 5 km on each side. Leave coasts, borders and polar areas blank.
    grid = np.full((900, 1800), "", dtype="U2")
    for feature in json.loads(raw)["features"]:
        iso = feature["properties"]["ISO_A2_EH"]
        if len(iso) != 2 or not iso.isalpha():
            continue
        geom = shapely.make_valid(shape(feature["geometry"]))
        shapely.prepare(geom)
        west,south,east,north = geom.bounds
        cols = np.arange(max(0,int((west+180)*5)),min(1800,int((east+180)*5)+1))
        xs = cols / 5 - 180
        for row in range(max(50,int((south+90)*5)), min(850,int((north+90)*5)+1)):
            y = row / 5 - 90
            dx = 0.05 / np.cos(np.radians(max(abs(y),abs(y+0.2))+0.05))
            inside = shapely.contains(geom, shapely.box(xs-dx,y-0.05,xs+0.2+dx,y+0.25))
            dest = cols[inside]
            grid[row,dest] = np.where(grid[row,dest]=="",iso,"??")
    rows = []
    for row in grid:
        breaks = np.flatnonzero(row[:-1] != row[1:])+1
        rows.append([[int(a),int(b),str(row[a])] for a,b in zip(np.r_[0,breaks],np.r_[breaks,len(row)]) if row[a] not in ("","??")])
    output.write_text(json.dumps(rows,separators=(",",":"))+"\n",encoding="utf-8")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source",type=Path)
    parser.add_argument("output",type=Path)
    args = parser.parse_args()
    build(args.source,args.output)
