#!/usr/bin/env bash
# 隧道体检脚本 —— 构建前端之前先跑这个，1 秒判定地址能不能用
#
# 用法（在 Git Bash 里）：
#   bash class-assistant/scripts/check-tunnel.sh frp-sea.com:46289
#   bash class-assistant/scripts/check-tunnel.sh cn-bj.natfrp.cloud:23456
#
# 判定标准只有一条：能不能拿到 {"code":1002}（未登录 = 后端活着且拦截器生效）

set -u
addr="${1:-}"
if [ -z "$addr" ]; then
    echo "用法: bash check-tunnel.sh 域名:端口"
    echo "例如: bash check-tunnel.sh cn-bj.natfrp.cloud:23456"
    exit 1
fi

PATHQ="/api/teacher/home/overview"
probe() {  # $1=协议
    local scheme="$1"
    curl -sk --noproxy '*' --max-time 15 \
         -o /tmp/tunnel_body.$$ -w '%{http_code}' \
         "$scheme://$addr$PATHQ" 2>/dev/null
}

echo "探测地址: $addr"
echo "-----------------------------------------------"

http_code=$(probe http)
http_body=$(head -c 200 /tmp/tunnel_body.$$ 2>/dev/null | tr -d '\n')

https_code=$(probe https)
https_body=$(head -c 200 /tmp/tunnel_body.$$ 2>/dev/null | tr -d '\n')
rm -f /tmp/tunnel_body.$$

echo "明文 http  -> HTTP $http_code"
if [ -n "$http_body" ] && [ "$http_code" != "000" ]; then echo "            $http_body"; fi
echo "加密 https -> HTTP $https_code"
if [ -n "$https_body" ] && [ "$https_code" != "000" ]; then echo "            $https_body"; fi
echo "-----------------------------------------------"

ok() { case "$1" in *'"code":1002'*) return 0;; *) return 1;; esac; }

if ok "$http_body"; then
    echo "结论: TCP 隧道，可用"
    echo ""
    echo "把这一行写进 .env.production（照抄，含末尾 /api）:"
    echo "    VITE_API_BASE_URL=http://$addr/api"
    echo ""
    echo "然后 npm run build:mp-weixin，重新导入 dist/build/mp-weixin，"
    echo "真机上点「···」→「打开调试」。"
    exit 0
fi

if ok "$https_body"; then
    echo "结论: 隧道通（HTTPS），下面看证书"
    issuer=$(echo | openssl s_client -connect "$addr" -servername "${addr%%:*}" 2>/dev/null \
             | openssl x509 -noout -issuer 2>/dev/null)
    case "$issuer" in
        *SakuraFrp*|*self*) echo "      证书: 自签（$issuer）"
                            echo "      微信真机可能拒自签证书 —— 只能实测一次：构建后手机开「打开调试」试" ;;
        "")                 echo "      证书: 读不到（端口可能不是 TLS）" ;;
        *)                  echo "      证书: $issuer（受信任的话微信能直接过）" ;;
    esac
    echo ""
    echo "把这一行写进 .env.production（照抄，含末尾 /api）:"
    echo "    VITE_API_BASE_URL=https://$addr/api"
    exit 0
fi

if [ "$http_code" = "501" ]; then
    echo "结论: 明文 HTTP 被 SakuraFrp 节点拦了（跟隧道类型无关）"
    echo "      实测 HTTP 隧道、TCP 隧道都一样被拦。拦你的是节点，不是微信，"
    echo "      所以「打开调试」也救不了。"
    echo ""
    echo "去做: 在隧道设置里把「自动 HTTPS」从禁用改成启用，保存并重启隧道，"
    echo "      拿到（可能变了）的新地址后重新跑本脚本。"
    exit 2
fi

if [ "$http_code" = "000" ] && [ "$https_code" = "000" ]; then
    echo "结论: 完全连不上"
    echo "      ① 隧道是不是没启动？② 地址或端口抄错？③ 电脑防火墙拦了 8080？"
    echo "      ④ 后端还在跑吗？（另开终端: curl -s http://127.0.0.1:8080/api/teacher/home/overview）"
    exit 3
fi

echo "结论: 能连上但返回的不是预期内容，把上面的输出发给我看"
exit 4
