package com.walker.servestatic;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import io.vertx.core.AsyncResult;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.gradle.api.*;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import javax.annotation.processing.Generated;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;


public class GenerateResourceModuleTask extends DefaultTask {
    private FileCollection webResources;
    private File packageDirectory;
    private File moduleDirectory;
    private String sourcePackage;
    private String name;
    private List<String> encodings;

    @InputFiles
    public FileCollection getWebResources() {
        return webResources;
    }

    public void setWebResources(FileCollection webResources) {
        this.webResources = webResources;
    }

    @OutputDirectory
    public File getPackageDirectory() {
        return packageDirectory;
    }

    public void setPackageDirectory(File packageDirectory) {
        this.packageDirectory = packageDirectory;
    }

    @InputDirectory
    public File getModuleDirectory() {
        return moduleDirectory;
    }

    public void setModuleDirectory(File moduleDirectory) {
        this.moduleDirectory = moduleDirectory;
    }

    public String getNameProp() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSourcePackage() {
        return sourcePackage;
    }

    public void setSourcePackage(String sourcePackage) {
        this.sourcePackage = sourcePackage;
    }

    public List<String> getEncodings() {
        return encodings;
    }

    public void setEncodings(List<String> encodings) {
        this.encodings = encodings;
    }

    private String generatedClassName() {
        return name.substring(0, 1).toUpperCase() + name.substring(1) + "Module";
    }

    private String filenameToBufferName(String filename) {
        int index;
        while((index = filename.indexOf('.')) != -1 || (index = filename.indexOf('-')) != -1) {
            filename = filename.substring(0, index) + filename.substring(index + 1, index + 2).toUpperCase() + filename.substring(index + 2);
        }
        return filename.replace("@", "");
    }

    private String filenameToWriteResponseMethodName(String filename) {
        String bufferName = filenameToBufferName(filename);
        return "write" + bufferName.substring(0, 1).toUpperCase() + bufferName.substring(1) + "Response";
    }

    private MethodDeclaration generateLoadClasspathResourceToBufferMethod(ClassOrInterfaceDeclaration moduleClass) {
        MethodDeclaration method = moduleClass.addMethod("loadClasspathResourceToBuffer", Modifier.PRIVATE, Modifier.STATIC)
                .setType(Buffer.class);

        Parameter pathParam = method.addAndGetParameter(String.class, "path");

        TryStmt tryStmt = method.createBody().addAndGetStatement(new TryStmt()).asTryStmt();
        VariableDeclarator dataDeclarator = tryStmt.getTryBlock()
                .addAndGetStatement(new VariableDeclarationExpr(new VariableDeclarator(
                        new ArrayType(new PrimitiveType(PrimitiveType.Primitive.BYTE)),
                        "data",
                        new MethodCallExpr(
                                new MethodCallExpr(
                                        new MethodCallExpr(
                                                new MethodCallExpr(
                                                        JavaParser.parseClassOrInterfaceType("Thread").getNameAsExpression(),
                                                        "currentThread"
                                                ),
                                                "getContextClassLoader"
                                        ),
                                        "getResourceAsStream",
                                        new NodeList<>(pathParam.getNameAsExpression())
                                ),
                                "readAllBytes"
                        ))))
                .asExpressionStmt().getExpression().asVariableDeclarationExpr().getVariable(0);
        tryStmt.getTryBlock()
                .addStatement(new ReturnStmt(
                        new MethodCallExpr(
                                JavaParser.parseClassOrInterfaceType("Buffer").getNameAsExpression(),
                                "buffer",
                                new NodeList<>(dataDeclarator.getNameAsExpression())
                        )
                ));

        CatchClause catchClause = new CatchClause();
        Parameter ioExceptionParam = catchClause.getParameter().setType(IOException.class).setName("e");
        catchClause.createBody().addStatement(new ThrowStmt(
                new ObjectCreationExpr(
                        null,
                        JavaParser.parseClassOrInterfaceType("IllegalStateException"),
                        new NodeList<>(new BinaryExpr(new StringLiteralExpr("Failed to load resource from classpath: "), pathParam.getNameAsExpression(), BinaryExpr.Operator.PLUS),
                                ioExceptionParam.getNameAsExpression())
                )
        ));

        tryStmt.getCatchClauses().add(catchClause);

        return method;
    }

    @TaskAction
    public void generateSources() {
        System.out.println("Generating " + name);

        Path generatedJavaPath = packageDirectory.toPath().resolve(generatedClassName() + ".java");

        CompilationUnit moduleCompilationUnit = new CompilationUnit(sourcePackage)
                .setStorage(generatedJavaPath)
                .addImport(Buffer.class)
                .addImport(HttpHeaders.class)
                .addImport(Router.class)
                .addImport(RoutingContext.class)
                .addImport(IOException.class)
                .addImport(HttpMethod.class)
                .addImport(AsyncResult.class);

        ClassOrInterfaceDeclaration moduleClass = moduleCompilationUnit
                .addClass(generatedClassName())
                .setPublic(true)
                .addSingleMemberAnnotation(Generated.class, "\"serve-static-resources\"")
                .setJavadocComment("Generated class to serve static files from the " + name + " web module.");

        MethodDeclaration loadResourceMethod = generateLoadClasspathResourceToBufferMethod(moduleClass);
        BlockStmt staticInitializer = moduleClass.addStaticInitializer();

        // Extension -> Uncompressed filepath -> Encoding -> Buffer Name
        HashMap<String, HashMap<Path, HashMap<String, NameExpr>>> resourcesMap = new HashMap<>();

        webResources.forEach(file -> {
            String resourceName = file.getName();
            String encoding = encodings.stream().filter(enc -> file.getName().endsWith(enc)).findFirst().orElse("");

            // Get the raw filepath and extension
            Path uncompressedFilepath = file.toPath();
            if(!encoding.isEmpty()) {
                uncompressedFilepath = uncompressedFilepath.getParent()
                        .resolve(resourceName.substring(0, resourceName.length() - (encoding.length() + 1)));
            }
            String rawFilename = uncompressedFilepath.getFileName().toString();
            String rawExtension = rawFilename.substring(rawFilename.lastIndexOf('.') + 1);

            // Add field to the class to store a buffer
            String fieldName = filenameToBufferName(resourceName);
            FieldDeclaration field = moduleClass.addField(Buffer.class, fieldName, Modifier.PUBLIC, Modifier.STATIC);

            // Add file to the resources map, to later use in the addRoutes() method
            resourcesMap.computeIfAbsent(rawExtension, ext -> new HashMap<>())
                    .computeIfAbsent(uncompressedFilepath, path -> new HashMap<>())
                    .put(encoding, field.getVariable(0).getNameAsExpression());

            // Add the file loading to the static initializer
            staticInitializer.addStatement(new AssignExpr(
                    field.getVariable(0).getNameAsExpression(),
                    new MethodCallExpr(
                            null,
                            loadResourceMethod.getName(),
                            new NodeList<>(new StringLiteralExpr(moduleDirectory.toPath().relativize(file.toPath()).toString().replace('\\', '/')))
                    ),
                    AssignExpr.Operator.ASSIGN
            ));
        });

        // Extension -> Uncompressed path -> Serve Method
        HashMap<String, HashMap<Path, MethodDeclaration>> serveMethodMap = new HashMap<>();

        resourcesMap.forEach((extension, resources) -> {
            resources.forEach((uncompressedPath, fileEncodings) -> {
                MethodDeclaration writeMethod = moduleClass
                        .addMethod(filenameToWriteResponseMethodName(uncompressedPath.getFileName().toString()))
                        .addModifier(Modifier.PUBLIC, Modifier.STATIC);
                Parameter resultResponse = writeMethod.addAndGetParameter(HttpServerResponse.class, "result");
                Parameter acceptEncoding = writeMethod.addAndGetParameter(String.class, "acceptEncoding");

                AtomicReference<String> mimeType = new AtomicReference<>();
                try {
                    mimeType.set(Files.probeContentType(uncompressedPath));
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }

                // Special case for mjs, since #probeContentType doesn't recognize them.
                if(extension.equalsIgnoreCase("mjs")) {
                    mimeType.compareAndSet(null, "text/javascript");
                }

                AtomicReference<Statement> serveResponseBlock = new AtomicReference<>(new BlockStmt()
                        .addStatement(new MethodCallExpr(
                                new MethodCallExpr(
                                        resultResponse.getNameAsExpression(),
                                        "putHeader",
                                        new NodeList<>(
                                                new FieldAccessExpr(
                                                        JavaParser.parseClassOrInterfaceType("HttpHeaders").getNameAsExpression(),
                                                        "CONTENT_TYPE"),
                                                new StringLiteralExpr(mimeType.get())
                                        )
                                ),
                                "end",
                                new NodeList<>(fileEncodings.get(""))
                        )));

                this.encodings.stream().sorted(Collections.reverseOrder())
                        .forEach(enc -> {
                            if (fileEncodings.containsKey(enc)) {
                                // Create new layer for if tree with new content encoding
                                IfStmt ifLayer = new IfStmt()
                                        .setCondition(new MethodCallExpr(
                                                acceptEncoding.getNameAsExpression(),
                                                "contains",
                                                new NodeList<>(new StringLiteralExpr(enc))
                                        ))
                                        .setElseStmt(serveResponseBlock.get())
                                        .setThenStmt(new BlockStmt()
                                                .addStatement(
                                                        new MethodCallExpr(
                                                                new MethodCallExpr(
                                                                        new MethodCallExpr(
                                                                                resultResponse.getNameAsExpression(),
                                                                                "putHeader",
                                                                                new NodeList<>(
                                                                                        new FieldAccessExpr(
                                                                                                JavaParser.parseClassOrInterfaceType("HttpHeaders").getNameAsExpression(),
                                                                                                "CONTENT_TYPE"
                                                                                        ),
                                                                                        new StringLiteralExpr(mimeType.get())
                                                                                )
                                                                        ),
                                                                        "putHeader",
                                                                        new NodeList<>(
                                                                                new FieldAccessExpr(
                                                                                        JavaParser.parseClassOrInterfaceType("HttpHeaders").getNameAsExpression(),
                                                                                        "CONTENT_ENCODING"
                                                                                ),
                                                                                new StringLiteralExpr(enc)
                                                                        )
                                                                ),
                                                                "end",
                                                                new NodeList<>(fileEncodings.get(enc))
                                                        )
                                                ));
                                serveResponseBlock.set(ifLayer);
                            }
                        });

                writeMethod.setBody(new BlockStmt().addStatement(serveResponseBlock.get()));

                serveMethodMap.computeIfAbsent(extension, ext -> new HashMap<>())
                        .put(uncompressedPath, writeMethod);
            });
        });

        // Setup addRoutes method
        MethodDeclaration addRoutes = moduleClass.addMethod("addRoutes")
                .addModifier(Modifier.PUBLIC, Modifier.STATIC)
                .setType(Router.class);
        Parameter addRoutesRouter = addRoutes.addAndGetParameter(Router.class, "router");
        Parameter addRoutesBaseUrl = addRoutes.addAndGetParameter(String.class, "baseUrl");
        Parameter addRoutesExtensions = addRoutes.addAndGetParameter(String.class, "extensions").setVarArgs(true);

        ForeachStmt addRoutesForeach = new ForeachStmt()
                .setVariable(new VariableDeclarationExpr(JavaParser.parseClassOrInterfaceType("String"), "ext"))
                .setIterable(addRoutesExtensions.getNameAsExpression());
        SwitchStmt addRoutesSwitch = addRoutes.createBody()
                .addAndGetStatement(addRoutesForeach).asForeachStmt()
                .createBlockStatementAsBody()
                .addAndGetStatement(new SwitchStmt()).asSwitchStmt()
                .setSelector(addRoutesForeach.getVariable().getVariable(0).getNameAsExpression());

        // Setup serverPushResources method
        MethodDeclaration serverPush = moduleClass.addMethod("serverPushResources")
                .addModifier(Modifier.PUBLIC, Modifier.STATIC);
        Parameter serverPushResponse = serverPush.addAndGetParameter(HttpServerResponse.class, "response");
        Parameter serverPushAcceptEncoding = serverPush.addAndGetParameter(String.class, "acceptEncoding");
        Parameter serverPushBaseUrl = serverPush.addAndGetParameter(String.class, "baseUrl");
        Parameter serverPushExtensions = serverPush.addAndGetParameter(String.class, "extensions").setVarArgs(true);

        ForeachStmt serverPushForeach = new ForeachStmt()
                .setVariable(new VariableDeclarationExpr(JavaParser.parseClassOrInterfaceType("String"), "ext"))
                .setIterable(serverPushExtensions.getNameAsExpression());
        SwitchStmt serverPushSwitch = serverPush.createBody()
                .addAndGetStatement(serverPushForeach).asForeachStmt()
                .createBlockStatementAsBody()
                .addAndGetStatement(new SwitchStmt()).asSwitchStmt()
                .setSelector(serverPushForeach.getVariable().getVariable(0).getNameAsExpression());

        // Fill content for addRoutes and serverPush
        serveMethodMap.forEach((extension, resources) -> {
            SwitchEntryStmt addRoutesSwitchEntry = new SwitchEntryStmt()
                    .setLabel(new StringLiteralExpr(extension));
            SwitchEntryStmt serverPushSwitchEntry = new SwitchEntryStmt()
                    .setLabel(new StringLiteralExpr(extension));

            resources.forEach((uncompressedPath, serveMethod) -> {
                String relativeUncompressed = moduleDirectory.toPath().relativize(uncompressedPath).toString().replace('\\', '/');

                // Generate addRoutes entry
                BinaryExpr addRoutesFullUrlExpr = new BinaryExpr(addRoutesBaseUrl.getNameAsExpression(), new StringLiteralExpr(relativeUncompressed), BinaryExpr.Operator.PLUS);

                Parameter contextParameter = new Parameter(JavaParser.parseClassOrInterfaceType("RoutingContext"), "context");
                VariableDeclarationExpr acceptEncodingDeclr = new VariableDeclarationExpr(
                        new VariableDeclarator(
                                JavaParser.parseClassOrInterfaceType("String"),
                                "acceptEncoding",
                                new MethodCallExpr(
                                        new MethodCallExpr(
                                                contextParameter.getNameAsExpression(),
                                                "request"),
                                        "getHeader",
                                        new NodeList<>(
                                                new FieldAccessExpr(
                                                        JavaParser.parseClassOrInterfaceType("HttpHeaders").getNameAsExpression(),
                                                        "ACCEPT_ENCODING"
                                                )
                                        )
                                )
                        )
                );

                LambdaExpr addRoutesHandlerLambda = new LambdaExpr()
                        .setParameters(new NodeList<>(contextParameter))
                        .setEnclosingParameters(true)
                        .setBody(new BlockStmt()
                        .addStatement(acceptEncodingDeclr)
                        .addStatement(new MethodCallExpr(
                                moduleClass.getNameAsExpression(),
                                serveMethod.getName(),
                                new NodeList<>(
                                        new MethodCallExpr(
                                                contextParameter.getNameAsExpression(),
                                                "response"
                                        ),
                                        acceptEncodingDeclr.getVariable(0).getNameAsExpression()
                                )
                        )));

                addRoutesSwitchEntry.addAndGetStatement(new ExpressionStmt(
                        new MethodCallExpr(
                                new MethodCallExpr(
                                        addRoutesRouter.getNameAsExpression(),
                                        "route",
                                        new NodeList<>(addRoutesFullUrlExpr)
                                ),
                                "handler",
                                new NodeList<>(addRoutesHandlerLambda)
                        )
                ));

                // Generate serverPush Entry
                BinaryExpr serverPushFullUrlExpression = new BinaryExpr(serverPushBaseUrl.getNameAsExpression(), new StringLiteralExpr(relativeUncompressed), BinaryExpr.Operator.PLUS);
                Parameter asyncResultParam = new Parameter(new ClassOrInterfaceType().setName(new SimpleName("AsyncResult")).setTypeArguments(JavaParser.parseClassOrInterfaceType("HttpServerResponse")), "ar");

                LambdaExpr serverPushHandlerLambda = new LambdaExpr()
                        .setParameters(new NodeList<>(asyncResultParam))
                        .setEnclosingParameters(true)
                        .setBody(new BlockStmt()
                                .addAndGetStatement(new IfStmt()).asIfStmt()
                                .setCondition(new MethodCallExpr(asyncResultParam.getNameAsExpression(), "succeeded"))
                                .setThenStmt(new BlockStmt()).getThenStmt().asBlockStmt()
                                .addStatement(new MethodCallExpr(
                                        moduleClass.getNameAsExpression(),
                                        serveMethod.getName(),
                                        new NodeList<>(
                                                new MethodCallExpr(asyncResultParam.getNameAsExpression(), "result"),
                                                serverPushAcceptEncoding.getNameAsExpression()
                                        )
                                )));

                serverPushSwitchEntry.addAndGetStatement(new MethodCallExpr(
                        serverPushResponse.getNameAsExpression(),
                        "push",
                        new NodeList<>(
                                new FieldAccessExpr(
                                        JavaParser.parseClassOrInterfaceType("HttpMethod").getNameAsExpression(),
                                        "GET"
                                ),
                                serverPushFullUrlExpression,
                                serverPushHandlerLambda
                        )
                ));
            });

            addRoutesSwitchEntry.addStatement(new BreakStmt().removeLabel());
            serverPushSwitchEntry.addStatement(new BreakStmt().removeLabel());

            addRoutesSwitch.getEntries().add(addRoutesSwitchEntry);
            serverPushSwitch.getEntries().add(serverPushSwitchEntry);
        });

        addRoutes.getBody().orElseThrow(AssertionError::new).addStatement(new ReturnStmt(addRoutesRouter.getNameAsExpression()));

        moduleCompilationUnit.getStorage().ifPresent(CompilationUnit.Storage::save);
    }
}
