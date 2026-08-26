# ============================================================
# 短链接平台 Dockerfile（多阶段构建）
# ============================================================

# ----- 阶段 1：构建（用带 JDK 的 Maven 镜像编译打包）-----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# 先只复制 pom.xml → 预下载依赖
# 关键：依赖没变时，这一层命中缓存，不用重新下载（构建提速）
COPY pom.xml .
RUN mvn -B dependency:go-offline

# 再复制源码编译打包（源码变化才触发重新编译）
COPY src ./src
RUN mvn -B clean package -DskipTests

# ----- 阶段 2：运行（只带 JRE，镜像体积小、更安全）-----
FROM eclipse-temurin:17-jre
WORKDIR /app

# 从构建阶段复制产物 jar（最终镜像不含 Maven/JDK 编译工具）
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
