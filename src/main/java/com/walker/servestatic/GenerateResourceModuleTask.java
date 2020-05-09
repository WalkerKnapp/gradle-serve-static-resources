package com.walker.servestatic;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import io.vertx.core.AsyncResult;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.gradle.api.*;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.impldep.org.mozilla.javascript.ast.NewExpression;
import org.gradle.internal.impldep.org.mozilla.javascript.ast.VariableDeclaration;

import javax.annotation.processing.Generated;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;


public class GenerateResourceModuleTask extends DefaultTask {
    private FileCollection webResources;
    private File packageDirectory;
    private String sourcePackage;
    private String name;
    private String[] encodings;

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

    public String[] getEncodings() {
        return encodings;
    }

    public void setEncodings(String[] encodings) {
        this.encodings = encodings;
    }

    private String generatedClassName() {
        return name.substring(0, 1).toUpperCase() + name.substring(1) + "Module";
    }

    private MethodDeclaration generateLoadClasspathResourceToBufferMethod(CompilationUnit moduleCompilationUnit, ClassOrInterfaceDeclaration moduleClass) {
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
                                                        new ClassExpr(JavaParser.parseClassOrInterfaceType("Thread")),
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
                                new ClassExpr(JavaParser.parseClassOrInterfaceType("Buffer")),
                                "buffer",
                                new NodeList<>(dataDeclarator.getNameAsExpression())
                        )
                ));

        CatchClause catchClause = new CatchClause();
        Parameter ioExceptionParam = catchClause.getParameter().setType(IOException.class).setName("e");
        catchClause.createBody().addStatement(new ThrowStmt(
                new ObjectCreationExpr(
                        null,
                        JavaParser.parseClassOrInterfaceType("IOException"),
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

        generateLoadClasspathResourceToBufferMethod(moduleCompilationUnit, moduleClass);


        webResources.forEach(file -> {

        });
    }
}
