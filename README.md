# WCDK R2DBC Support for IntelliJ IDEA

This plugin adds navigation support between WCDK R2DBC repository XML files and Java repositories.

## Features

- `namespace="com.example.UserRepository"` jumps to the repository interface.
- Statement `id="findActiveByEmail"` jumps to the matching repository method.
- `resultType`, `type`, `javaType`, and `parameterType` jump to Java classes.
- `property` jumps to an entity field or accessor method.
- `resultMap="UserMap"` (including a qualified `Repository.UserMap`) jumps to the matching `<resultMap id="UserMap">` declaration.
- `#{parameter}` and `:parameter` references jump to repository method parameters or entity properties when they can be resolved.
- WCDK logo gutter markers are shown beside recognized XML references and Java repository classes/methods, including `parameterType` and `resultMap` references.
- Clicking the gutter marker beside a repository class or method opens its associated XML file or statement, matching the Java/XML round-trip workflow provided by MyBatisX.
- The full WCDK logo is packaged as a plugin resource, while the compact WCDK mark is used in the 16px editor gutter.

## Supported XML shape

```xml
<repository namespace="com.example.repository.UserRepository">
    <select id="findActiveByEmail" resultType="com.example.User">
        SELECT id, user_name
        FROM sys_user
        WHERE email = #{email}
    </select>
</repository>
```

## Build

The plugin uses Maven and targets IntelliJ IDEA Community 2024.3. JetBrains officially recommends its Gradle plugin for full IntelliJ Platform tooling; this Maven setup focuses on standard compilation and local ZIP packaging.

```bash
mvn clean package
```

The generated ZIP is placed under `target/` and can be installed from **Settings | Plugins | Install Plugin from Disk**. The first build downloads the IntelliJ IDEA Community SDK ZIP, so it may take several minutes.
