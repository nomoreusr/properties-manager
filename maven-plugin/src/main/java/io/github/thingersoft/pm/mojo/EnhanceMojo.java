package io.github.thingersoft.pm.mojo;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.LoaderClassPath;

@Mojo(name = EnhanceMojo.GOAL, defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class EnhanceMojo extends AbstractMojo {

	public static final String GOAL = "enhance";

	@Parameter(property = "project", defaultValue = "${project}")
	private MavenProject project;

	@Parameter(defaultValue = "true", property = "javassist.includeTestClasses", required = true)
	private Boolean includeTestClasses;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {

		ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();

		try {
			List<URL> classPath = new ArrayList<URL>();
			for (final Object runtimeResource : project.getRuntimeClasspathElements()) {
				classPath.add(resolveUrl((String) runtimeResource));
			}
			String targetClassesDirectory = project.getBuild().getOutputDirectory();
			classPath.add(resolveUrl(targetClassesDirectory));
			ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
			URLClassLoader pluginClassLoader = URLClassLoader.newInstance(classPath.toArray(new URL[classPath.size()]), contextClassLoader);
			Thread.currentThread().setContextClassLoader(pluginClassLoader);

			try (ScanResult scanResult = new ClassGraph().enableAllInfo().scan()) {
				ClassInfoList classInfoList = scanResult.getClassesWithAnnotation(io.github.thingersoft.pm.api.annotations.Properties.class.getName());
				for (ClassInfo mappedClassInfo : classInfoList) {
					// found class annotated with @Properties
					ClassPool classPool = new ClassPool(ClassPool.getDefault());
					classPool.childFirstLookup = true;
					classPool.appendClassPath(targetClassesDirectory);
					classPool.appendClassPath(new LoaderClassPath(Thread.currentThread().getContextClassLoader()));
					classPool.appendSystemPath();
					CtClass ctMappedClass = classPool.get(mappedClassInfo.getName());
					CtConstructor initializer = ctMappedClass.makeClassInitializer();
					initializer.setBody("io.github.thingersoft.pm.api.PropertiesStore.checkInitByAnnotatedClass(" + mappedClassInfo.getName() + ".class);");
					ctMappedClass.writeFile(targetClassesDirectory);
				}
			}

		} catch (Exception e) {
			getLog().error(e.getMessage(), e);
			throw new MojoExecutionException(e.getMessage(), e);
		} finally {
			Thread.currentThread().setContextClassLoader(originalContextClassLoader);
		}

	}

	private URL resolveUrl(final String resource) {
		try {
			return new File(resource).toURI().toURL();
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

}
