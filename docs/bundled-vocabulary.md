# 内置词库 assets 与数据库

词库来源：[The Ultimate Guide to Russian – The Most Used 5000 Words](https://ankiweb.net/shared/info/1147549038)。

App 在首次运行时自动将内置 JSON 词库写入 Room。开发阶段的 Python 脚本用于离线生成词库资源。

## 生成 assets

仅需要 Python 3 标准库。在仓库根目录执行：

```sh
python3 tools/apkg_to_assets.py The_Ultimate_Guide_to_Russian__The_Most_Used_5000_Words.apk.1.1g
```

脚本按包内容读取，不依赖扩展名。当前只处理该词库的 `collection.anki2` 及 JSON 媒体索引，不兼容任意 Anki 笔记结构。

可选参数：

- `--assets-dir PATH`：指定 assets 根目录，默认 `app/src/main/assets`。
- `--book-id ID`：单词本的稳定标识，默认 `russian-5000`。

输出结构：

```text
app/src/main/assets/vocabulary/
├── words.json
└── audio/
    ├── 0.mp3
    └── ...
```

每次成功转换会替换指定 assets 根目录下的整个 `vocabulary/`，因此中文释义等人工补充内容应另行保存，避免重新生成时被覆盖。转换异常时保留此前的输出。

## 数据梳理

- 只读取词汇 notes：5000 条词汇生成 5000 条应用记录，跳过 4 条介绍笔记。
- 不读取来源 cards，不保留其练习方向或学习进度。
- `Word` → `word`；`Meaning` → `englishTranslation`；`Tag` → `partOfSpeech`。
- 转换脚本将 `chineseTranslation` 初始设为空字符串；当前打包 JSON 已补充中文翻译，入库时直接保留。
- `Frequency` → `sortOrder`，按词频升序输出。
- `Audio` → `wordAudio`。
- `First/Second/Third/Fourth Exs` → `sentences`。
- `First/Second/Third/Fourth Tra` → `sentenceTranslations`（英文）。
- `1st/2nd/3rd/4th Audio` → `sentenceAudios`。
- 删除 HTML 标签，保留文字、重音符号和基本换行。
- 例句、翻译、音频全空的位置直接舍弃；其余位置缺少内容时使用空字符串。不额外校验列表之间的对应关系。
- 只导出引用到的音频，使用原包的数字编号命名；丢弃图片、模板、CSS、Length 字段、来源标签及其他无关内容。

当前词库转换得到 5000 条单词记录和 10366 个 MP3。

## JSON 与入库约定

`words.json` 顶层包含 `bookId`、`words`。`words` 中每个对象包含以下与 Kotlin 实体同名的字段：

```json
{
  "word": "кто",
  "englishTranslation": "who",
  "chineseTranslation": "谁",
  "partOfSpeech": "pron",
  "wordAudio": "vocabulary/audio/123.mp3",
  "sentences": ["Кто это?"],
  "sentenceTranslations": ["Who is it?"],
  "sentenceAudios": ["vocabulary/audio/456.mp3"],
  "sortOrder": 64
}
```

示例音频编号仅用于说明格式。路径相对于 assets 根目录，播放时从 assets 读取，不应拼接 filesDir。

入库时将初始化代码指定的单词本标识（当前为 `俄语5000词`）与每个 word 写入数据库，包含 JSON 中已有的中文翻译。主键 `id` 自增，三个复习字段使用数据库默认值。

`WordCardApplication.onCreate()` 启动后台数据库初始化任务，打开 Room 数据库。`AnkiDatabase` 注册 `BundledVocabularyInitializer`，在 `RoomDatabase.Callback.onCreate()` 中同步读取 JSON 并写入词库。初始化属于数据库生命周期，Activity 不参与。

首次建库时，词库写入使用回调提供的数据库连接，与建表处于同一事务；不重新打开数据库，不通过 DAO 异步导入。全部写入成功后数据库才可供查询。失败会回滚建库事务并记录日志，下次打开时重试。`onOpen()` 检查已有库是否缺少内置单词本，仅在缺少时通过事务填充，覆盖开发阶段重建或此前留下空库的情况。正常打开已初始化数据库不会再次导入，也不会覆盖复习进度。音频路径直接存入数据库，不复制音频文件。WordCardsFragment 缓存当前卡片，单词按钮播放 `wordAudio`，例句按钮按列表顺序播放 `sentenceAudios` 中的非空音频，均使用 MediaPlayer 直接读取 assets 中的 MP3。点击任一播放按钮会停止之前的音频并重新开始；切词、页面停止或 View 销毁时停止播放并释放播放器。

## 数据库

数据库仍为 `wordcard-room.db`，Room 版本为 4，业务表为 `anki_cards`。

保留 `id`、`bookId` 作为记录和单词本标识；业务内容使用上述通用字段。不存储来源笔记 GUID 和单词本名称。三个例句列表分别由 `CardContentConverters` 转为 JSON TEXT 列，不再使用例句、动词变位等嵌套对象。

仅保留以下三个复习字段：

| Kotlin 字段 | SQLite 列 | 类型及初始值 |
| --- | --- | --- |
| `lastReviewedAt` | `last_reviewed_at` | 可空 Long，Unix 毫秒时间戳，新词为 null |
| `reviewCount` | `review_count` | Int，新词为 0 |
| `lastProficiency` | `last_proficiency` | Double，范围 0～1，初始为 0.0 |

新词通过 `reviewCount == 0` 判断。点击“认识”时调用 `recordReview(id, reviewedAt, proficiency)`，在一条 UPDATE 中同时更新时间和熟练度；按设备本地日期，今天尚未计过有效复习才将次数增加 1，今天已经计过则不再增加。

LearnAgain 只在 ViewModel 中累计当天的不认识次数，并将当前词随机回插到队列第 5 位至队尾，不写数据库。直到点击“认识”，按 `1 / (1 + 当天累计不认识次数)` 保存熟练度。同一个词当天的累计次数在提交后继续保留，跨天清零；退出进程后未持久化的次数不恢复。单纯翻卡不调用 `recordReview()`。

## 学习队列与补词

当前自动使用按词书标识排序的第一本词书。ViewModel 首次装入最多 20 个词，包含正在展示的队首；认识并出队后，队列少于 15 个时，每批补入最多 5 个词。候选不足则按实际数量补入，两类都没有候选时停止。

数据库先排除当前队列中的 ID，再读取候选词的轻量查询投影。Kotlin 分别排序、截取每类所需数量后，按选中的 ID 读取完整卡片，不修改数据库表结构。新词为 `reviewCount == 0`，按单词中字母数量升序排列，忽略重音组合符号、标点、空白等非字母字符；原词保持不变。相同长度按 `sortOrder`、`id` 排列。

复习词为 `reviewCount > 0` 且分数至少为 1 的词，计算规则为：

- `elapsedDays`：今天与 `lastReviewedAt` 的设备本地日期相差天数，不按经过的 24 小时计算。
- `expectedGapDays = max(1, sqrt(reviewCount) * lastProficiency)`。
- `priorityScore = elapsedDays / expectedGapDays`。

复习词按分数降序，相同分数按 `sortOrder`、`id` 排列。今天已认识的词相隔天数为 0，不进入复习候选。

补词从新词开始，按“新词、复习词”交替追加到队尾；某类不足时由另一类补足，下一次优先取与本次实际加入来源相反的类别。交替位置由 ViewModel 跨批次保存，LearnAgain 回插不改变它。补词不重排当前队列。

“上一个词”只缓存最近一次点击“认识”或 LearnAgain 的词。点击返回时消费并清空缓存，将该词放到队首，允许队列临时达到 21 个；没有缓存则直接返回。如果该词因 LearnAgain 仍在队列中，先移除原位置再置顶，保证不重复。返回不撤销学习记录、不改变交替补词位置。队列学完后保留操作区，仍可返回最后一个词。

开发阶段不提供数据库迁移，表结构升级时重建实验数据库。版本 4 的 Room schema 由编译时 KSP 导出到 `app/schemas/`。
