# Code Repository Search Engine — Frontend

代码仓库搜索引擎前端，运行在 `3000` 端口，页面采用深色 Hero 设计：

- 深色渐变 Hero、搜索框和推荐关键词；
- 结果总数与 Jieba 分词标签；
- 仓库结果卡片：仓库名、地址和 README；
- README 命中关键词红色高亮，并自动滚动到第一处命中；
- 每页 10 条，51 条结果展示为 6 页；
- 移动端单列布局。

![前端预览](preview.png)

## 运行

环境要求：Node.js 24.x、npm 11.x。

```powershell
cd frontend
npm install
npm start
```

打开：

```text
http://localhost:3000
```

默认后端地址：

```text
http://localhost/search
```

如后端地址不同，通过环境变量覆盖：

```powershell
$env:BACKEND_URL = "http://127.0.0.1/search"
$env:PORT = "3000"
npm start
```

## npm 缓存

`npm install` 默认使用：

```text
%LOCALAPPDATA%\npm-cache
```

本机当前为：

```text
C:\Users\27271\AppData\Local\npm-cache
```

`C:\JAVA\npm-cache` 是安装时临时指定的缓存目录，不属于项目，可以删除：

```powershell
Remove-Item "C:\JAVA\npm-cache" -Recurse -Force
```

删除缓存不会影响已安装的 `node_modules`。

## 功能

- 输入查询词后请求后端 `GET /search?query=...`；
- 服务端使用 `@node-rs/jieba` 对查询词执行结巴搜索引擎模式分词；
- 前端展示 `fullName`、`htmlUrl` 和 `readme`；
- README 中命中查询分词的文本使用红色高亮；
- 展示结果总数；
- 固定每页 10 条，客户端分页；
- 例如 51 条结果会展示 6 页：前 5 页每页 10 条，最后一页 1 条；
- 支持 URL 参数直接打开搜索页：

```text
http://localhost:3000/?query=数据结构
```

## 结构

```text
frontend/
├── server.js          Node HTTP 服务，静态资源与后端代理、Jieba 分词
├── public/
│   ├── index.html     页面结构
│   ├── styles.css     页面样式
│   └── app.js         请求、分页、高亮和交互
├── package.json
└── README.md
```

## 请求链路

```text
浏览器
  -> GET http://localhost:3000/api/search?query=数据结构
  -> Node 服务执行 Jieba 分词
  -> GET http://localhost/search?query=数据结构
  -> 返回结果和分词 tokens
  -> 浏览器分页渲染并高亮 README
```

浏览器不直接请求 Java 服务，因此不需要额外配置 CORS。

## 分页规则

页面每页固定 10 条：

```text
总条数 51
总页数 ceil(51 / 10) = 6
第 1-5 页：每页 10 条
第 6 页：1 条
```

分页在浏览器端完成，切换页码不会重复请求后端。
