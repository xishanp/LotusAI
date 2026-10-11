#!/usr/bin/env bash
# 用 mzhcloud 的 OpenAI 兼容 images/edits 生成/改图。
# 用法: tools/gen_image.sh <输入图> <输出图> <prompt 文件>
# ⚠️ key 只在环境变量里传，绝不写进这个文件。
set -euo pipefail
IN="$1"; OUT="$2"; PROMPT_FILE="$3"
: "${LOTUS_IMG_KEY:?需要先 export LOTUS_IMG_KEY=...}"
curl -sS --max-time 300 \
  -X POST "https://www.mzhcloud.com/v1/images/edits" \
  -H "Authorization: Bearer $LOTUS_IMG_KEY" \
  -F "model=gpt-image-2" \
  -F "image=@${IN}" \
  -F "prompt=<${PROMPT_FILE}" \
  -F "size=1024x1024" \
  -F "n=1" \
  -o "$OUT.json"
python3 - "$OUT.json" "$OUT" <<'PY'
import base64, json, sys
j, out = sys.argv[1], sys.argv[2]
d = json.load(open(j))
if "data" in d and d["data"]:
    item = d["data"][0]
    if item.get("b64_json"):
        open(out, "wb").write(base64.b64decode(item["b64_json"]))
        print("saved", out)
    elif item.get("url"):
        print("URL:", item["url"])
else:
    print("ERROR:", json.dumps(d)[:600])
PY
