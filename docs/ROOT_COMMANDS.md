# Root 命令接口

## 一圈即搜

Eta 暴露了一个独立的 Root `app_process` 入口，不依赖 Agent 对话，也不依赖 UI：

```bash
APK="$(pm path io.github.mangi.eta | sed 's/^package://')"
CLASSPATH="$APK" app_process /system/bin \
  io.github.mangi.eta.agent.phone.CircleSearchCommand
```

默认使用 `entryPoint = 2`。也可以显式传入：

```bash
CLASSPATH="$APK" app_process /system/bin \
  io.github.mangi.eta.agent.phone.CircleSearchCommand 2
```

可以把它封装成其他 Root 程序更容易调用的固定命令：

```bash
cat >/data/local/tmp/eta-circle-search <<'EOF'
#!/system/bin/sh
APK="$(pm path io.github.mangi.eta | sed 's/^package://')"
exec env CLASSPATH="$APK" /system/bin/app_process /system/bin io.github.mangi.eta.agent.phone.CircleSearchCommand "$@"
EOF
chmod 755 /data/local/tmp/eta-circle-search
```

之后其他 Root 程序直接执行 `/data/local/tmp/eta-circle-search`，或传
`/data/local/tmp/eta-circle-search 2` 指定 entry point。

行为：

- 成功：stdout 输出 `OK`，退出码 `0`；
- 系统没有暴露 `contextual_search` 服务或调用失败：stderr 输出
  `CIRCLE_TO_SEARCH_UNAVAILABLE`，退出码 `1`。

必须在 Android root shell 中运行，不能在 Eta 的 Linux chroot 里直接运行
`app_process`/`cmd`。如果由其他 Root 程序 fork 子进程，直接继承 root uid 后执行上面的
命令即可。

## 通用手机命令通道

`PhoneCommandMain` 也支持同一个工具，适合已经使用 Eta 手机命令协议的 Root 程序：

```bash
APK="$(pm path io.github.mangi.eta | sed 's/^package://')"

printf '%s' '{"version":1,"tool":"circle_to_search","arguments":{}}' |
  CLASSPATH="$APK" app_process /system/bin \
    io.github.mangi.eta.agent.phone.PhoneCommandMain
```

输出以 `ETA_PHONE_RESULT:` 开头，后续是 JSON：

```json
{"ok":true,"tool":"circle_to_search","status":"triggered"}
```

失败时返回：

```json
{"ok":false,"tool":"circle_to_search","code":"CIRCLE_TO_SEARCH_UNAVAILABLE"}
```

## activity intent

如果只是想测试当前 ROM 是否暴露了 Google 的 Contextual Search Activity，可以用：

```bash
cmd activity start-activity \
  -a android.app.contextualsearch.action.LAUNCH_CONTEXTUAL_SEARCH \
  -p com.google.android.googlequicksearchbox
```

这条命令只能启动 OEM 暴露的 Activity，不能保证进入 Circle to Search 的系统 overlay；
真正稳定的入口是上面的 `CircleSearchCommand`，它直接调用系统
`IContextualSearchManager.startContextualSearch()`。
