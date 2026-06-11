# NILDB

这是一个用 Java 实现的关系型数据库。



## 运行方式

首先执行如下命令，编译源码：

```
mvn compile
```

然后执行如下命令以 /tmp/nildb 作为路径创建数据库：

```
mvn exec:java -Dexec.mainClass="io.github.uipilnil.backend.Launcher" -Dexec.args="-create /tmp/nildb"
```

接着执行如下命令以默认参数启动数据库服务：

```
mvn exec:java -Dexec.mainClass="io.github.uipilnil.backend.Launcher" -Dexec.args="-open /tmp/nildb"
```

此时数据库已经启动在本机的 1201 端口。重新启动一个终端，执行如下命令启动客户端连接数据库：

```
mvn exec:java -Dexec.mainClass="io.github.uipilnil.client.Launcher"
```

此时会启动一个交互式命令行，在命令行中输入类 SQL 语句，点击回车会把语句发送到服务器，并输出执行结果。



## 架构

它的核心架构包括以下几层：

1. **TM 事务管理器**
    - **作用**：通过 XID 文件记录事务状态（活跃/已提交/已终止）。
2. **DM 数据管理器**
    - **作用**：负责存储数据、管理页面、崩溃恢复。
        - **PageCache**：实现了页面缓存，用于管理磁盘页和内存页的交换。
        - **PageIndex**：记录了空闲页的索引，用于分配存储空间。
        - **Logger**：实现了 WAL 写前日志。
        - **Recover**：实现了基于日志的崩溃恢复。
        - **DataItem**：这是数据条目，也就是页面内部存储的最小数据单元，每条记录对应一个 DataItem。
3. **VM 版本管理器**
    - **作用**：实现 MVCC 多版本并发控制。
        - **LockTable**：基于等待图实现了死锁检测，用于自动终止发生死锁的事务。
        - **Visibility**：用于判断数据版本对事务的可见性。
4. **IM 索引管理器**
    - **作用**：基于 B+ 树实现索引结构。
5. **TBM 表管理器**
    - **作用**：管理表结构、字段定义，把逻辑表映射到底层存储。
6. **Parser SQL 解析器**
    - **作用**：支持部分 SQL 语句，如 `create`、`insert`、`delete`、`update`、`select`、`drop`、`begin`、`commit`、`abort` 等。
7. **Server 和 Client**
    - **Server**：基于 socket 监听 3307 端口，接收客户端请求并执行 SQL。
    - **Client**：交互式的 shell，连接服务端发送 SQL 并展示结果。
    - **Transport**：自定义传输协议。

