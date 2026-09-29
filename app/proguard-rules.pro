# SQLCipher and Room publish the required rules in their artifacts.

# Gson 通过反射按字段名读写这些模型；混淆字段会破坏备份恢复与 AI 响应解析。
-keepclassmembers class com.relationship.graph.data.backup.BackupEnvelope { <fields>; }
-keepclassmembers class com.relationship.graph.data.backup.BackupPayload { <fields>; }
-keepclassmembers class com.relationship.graph.data.ai.AiActionPayload { <fields>; }
-keepclassmembers class com.relationship.graph.data.local.* { <fields>; }

# Gson 反序列化需要的数据类构造器。
-keep class com.relationship.graph.data.backup.BackupEnvelope { <init>(); }
-keep class com.relationship.graph.data.backup.BackupPayload { <init>(); }
-keep class com.relationship.graph.data.ai.AiActionPayload { <init>(); }

# Gson 2.10+ 自带 consumer 规则处理 TypeToken，这里补齐兜底。
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
