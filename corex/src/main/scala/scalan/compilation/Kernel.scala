package scalan.compilation

import java.io.File

import configs.syntax._
import com.typesafe.config.{ConfigFactory, Config}
import configs.Result.{Failure, Success}

import scalan.meta.ScalanAst.KernelType
import scalan.util.{ClassLoaderUtil, FileUtil}
import scalan.{Plugins, ScalanEx}

// TODO Split into AbstractKernel and FileSystemKernel?
class Kernel[+ScalanCake <: ScalanEx, A, B](
      val kernelName: String,
      val kernelType: KernelType,
      _kernelFunc: => ScalanCake#Rep[A => B],
      val compiler: Compiler[ScalanCake], dir: File, _config: Config)
  extends (A => B) {
  val scalan: compiler.scalan.type = compiler.scalan
  lazy val kernelFunc = _kernelFunc.asInstanceOf[scalan.Rep[A => B]]
  lazy val compilerConfig = _config.get[Config]("compiler") match {
    case Failure(_) =>
      compiler.defaultCompilerConfig
    case Success(config) =>
      compiler.compilerConfigFrom(config)
  }
  lazy val graphVizConfig = _config.get[Config]("graphviz") match {
    case Failure(_) =>
      GraphVizConfig.default
    case Success(config) =>
      GraphVizConfig.from(config)
  }
  lazy val compiled =
    compiler.buildExecutable(dir, kernelName, kernelFunc, graphVizConfig)(compilerConfig)
  lazy val eA = compiled.common.eInput
  lazy val eB = compiled.common.eOutput

  def apply(input: A) = compiler.execute(compiled, input)
}

// TODO add listKernels, loadKernel
abstract class KernelStore[+ScalanCake <: ScalanEx] {
  val scalan: ScalanCake
  val storeConfig: Config
  import Plugins.{configWithPlugins, pluginClassLoader}

  private val compilers = collection.mutable.Map.empty[KernelType, Compiler[scalan.type]]

  private def compiler(kernelType: KernelType): Compiler[scalan.type] = compilers.getOrElseUpdate(kernelType, {
    val confKey = s"backend.${kernelType.confKey}.compilerClass"
    configWithPlugins.get[String](confKey) match {
      case Failure(_) =>
        val msg =
          s"""Compiler class for kernel type ${kernelType.name} not found under scalan.$confKey in config including plugins.
              |Class path: ${ClassLoaderUtil.classPath(pluginClassLoader).map(_.getAbsolutePath).mkString(File.pathSeparator)}.
              |If necessary directory or jar file is missing, add it to ${Plugins.extraClassPathKey} property in application.conf or -D command line argument.""".stripMargin
        throw new CompilationException(msg, null)
      case Success(className) =>
        createCompiler(scalan, storeConfig, className)
    }
  })

  private def createCompiler(scalan: ScalanEx, config: Config, className: String): Compiler[scalan.type] = {
    val compilerClass = Plugins.loadClass(className)
    compilerClass.getConstructors match {
      case Array(constructor) =>
        constructor.getParameterTypes match {
          case Array(scalanRequiredClass) =>
            if (scalanRequiredClass.isInstance(scalan)) {
              constructor.newInstance(scalan).asInstanceOf[Compiler[scalan.type]]
            } else
              throw new IllegalArgumentException(s"$className requires ${scalanRequiredClass.getName}, but $scalan is not an instance.")
          case parameterClasses =>
            throw new IllegalArgumentException(s"The constructor of $className takes ${parameterClasses.length} parameters, must take 1 (Scalan instance)")
        }
      case constructors =>
        throw new IllegalArgumentException(s"$className has ${constructors.length} constructors, must have 1")
    }
  }

  def createKernel[A,B](kernelId: String, kernelType: KernelType, f: => scalan.Rep[A => B], kernelConfig: Config = ConfigFactory.empty()): Kernel[scalan.type, A, B] = {
    val allConfig = kernelConfig.withFallback(storeConfig)
    val compiler = this.compiler(kernelType)
    internalCreateKernel(kernelId, kernelType, f, compiler, allConfig)
  }

  def internalCreateKernel[A, B](kernelId: String, kernelType: KernelType, f: => scalan.Rep[(A) => B], compiler: Compiler[scalan.type], allConfig: Config): Kernel[scalan.type, A, B]
}

object KernelStore {
  def open(scalan: ScalanEx, baseDir: File, config: Config = ConfigFactory.empty()): KernelStore[scalan.type] = {
    val configFile = new File(baseDir, "kernelStore.conf")
    val config1 = if (configFile.exists())
      ConfigFactory.parseFile(configFile).withFallback(config)
    else
      config
    // TODO decide kernel store type based on config
    new FileSystemKernelStore[scalan.type](scalan, baseDir, config1)
  }
}

// TODO move methods for actually storing results from Compiler to here
class FileSystemKernelStore[+ScalanCake <: ScalanEx](val scalan: ScalanCake, val baseDir: File, val storeConfig: Config) extends KernelStore[ScalanCake] {
  def internalCreateKernel[A, B](kernelId: String, kernelType: KernelType, f: => scalan.Rep[(A) => B], compiler: Compiler[scalan.type], allConfig: Config): Kernel[scalan.type, A, B] = {
    if (FileUtil.isBadFileName(kernelId)) {
      throw new IllegalArgumentException(s"kernel id $kernelId contains special characters")
    }

    val dir = FileUtil.file(baseDir, kernelId, FileUtil.cleanFileName(kernelType.name))
    new Kernel(kernelId, kernelType, f, compiler, dir, allConfig)
  }
}
