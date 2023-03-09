import zio.{Scope, ZIO, ZIOAppArgs, ZIOAppDefault}
import java.io._
import scala.sys.process._

object Main extends ZIOAppDefault {
  // environment R, error E, succeed A
  case class Zio(typ: String, R: String, E: String, A: String)

  // max line count between two Rs and Es and As so we can better diff
  case class LinesCount(R: Int, E: Int, A: Int)

  // "found   : zio.URIO ..."
  def cleanFound(str: String) = str.trim.stripPrefix("found").trim.stripPrefix(":").trim

  // "required: ..."
  def cleanRequired(str: String) = str.trim.stripPrefix("required").trim.stripPrefix(":").trim

  // "(which expands to) ..."
  def cleanExpands(str: String) = str.trim.stripPrefix("(which expands to)").trim

  def splitWith(str: String) = {
    val lines = str.split(" with ").map(s => s.trim)
    lines.sorted.distinct.mkString("\n")
  }

  // split this: zio.ZIO[R...,E...,A...]
  // into:
  // typ = zio.ZIO
  // R = R...
  // E = E...
  // A = A...
  def makeZio(str: String) = {
    val typ = str.substring(0, str.indexOf("["))

    var middle = str.substring(str.indexOf("[") + 1, str.lastIndexOf("]"))
    val split = middle.split(",")

    val RLine = split(0)
    val ELine = if (split.length >= 2) split(1) else ""
    val ALine = if (split.length >= 3) split(2) else ""

    // split this into multiple lines, sort and unique: "zio.system.System with zio.logging.Logging with ..."
    val R = splitWith(RLine)
    val E = splitWith(ELine)
    val A = splitWith(ALine)

    Zio(typ, R, E, A)
  }

  def pipe() = {
    val isReader = new InputStreamReader(System.in);
    val bufReader = new BufferedReader(isReader);
    var inputStr = bufReader.readLine()
    var input = ""
    while (inputStr != null) {
      input += inputStr + "\n"
      inputStr = bufReader.readLine()
    }
    input
  }

  def writeToFile(writer: Writer, z: Zio, linesCount: LinesCount) =
    {
      //def indent(str: String) = str.split("\n").map(s => "  " + s).mkString("\n")
      def indent(str: String) = str

      writer.write(indent(z.typ) + "\n")

      writer.write("=== R ===\n")
      writer.write(indent(z.R) + "\n")
      List.fill(linesCount.R - z.R.split('\n').length)("\n").foreach(writer.write(_))

      writer.write("=== E ===\n")
      writer.write(indent(z.E) + "\n")
      List.fill(linesCount.E - z.E.split('\n').length)("\n").foreach(writer.write(_))

      writer.write("=== A ===\n")
      writer.write(indent(z.A) + "\n")
      List.fill(linesCount.A - z.A.split('\n').length)("\n").foreach(writer.write(_))
    }

  override def run: ZIO[Environment with ZIOAppArgs with Scope, Any, Any] = ZIO.succeed(run_)

  def run_ = {
    val input = pipe()

    // take everything after "found"
    val text = input.substring(input.indexOf("found"))

    // expecting 2-4 lines:
    // 1. found
    // 2. (which expands to) [optional]
    // 3. required
    // 4. (which expands to) [optional]
    val lines = text.split("\n")
    var line = 0

    val foundStr = cleanFound(lines(line)) // "found ..."
    line += 1

    // "(which expands to) ..."
    var foundExpandsStr = foundStr
    if (lines(line).contains("(which expands to)")) {
      foundExpandsStr = cleanExpands(lines(line))
      line += 1
    }

    val requiredStr = cleanRequired(lines(line)) // "required ..."
    line += 1

    // "(which expands to) ..."
    var requiredExpandsStr = requiredStr
    if (lines(line).contains("(which expands to)")) {
      requiredExpandsStr = cleanExpands(lines(line))
    }

    /*
    zio.URIO[zio... , ...]
    zio.ZIO[zio... , ... , ... ]
    zio.URIO[zio... , ...]
    zio.ZIO[zio...  , ... , ... ]
     */

    val found = makeZio(foundStr)
    val foundExpands = makeZio(foundExpandsStr)
    val required = makeZio(requiredStr)
    val requiredExpands = makeZio(requiredExpandsStr)

    val linesCount = LinesCount(
      math.max(found.R.split('\n').length, required.R.split('\n').length),
      math.max(found.E.split('\n').length, required.E.split('\n').length),
      math.max(found.A.split('\n').length, required.A.split('\n').length)
    )

    val expandsLinesCount = LinesCount(
      math.max(foundExpands.R.split('\n').length, requiredExpands.R.split('\n').length),
      math.max(foundExpands.E.split('\n').length, requiredExpands.E.split('\n').length),
      math.max(foundExpands.A.split('\n').length, requiredExpands.A.split('\n').length)
    )

    val foundFile = File.createTempFile("ziodiff-", "-found")
    val requiredFile = File.createTempFile("ziodiff-", "-required")

    val foundWriter = new BufferedWriter(new FileWriter(foundFile))
    val requiredWriter = new BufferedWriter(new FileWriter(requiredFile))

    foundWriter.write("=============\n")
    foundWriter.write("=== FOUND ===\n")
    foundWriter.write("=============\n")
    writeToFile(foundWriter, found, linesCount)
    foundWriter.write("==================\n")
    foundWriter.write("=== EXPANDS TO ===\n")
    foundWriter.write("==================\n")
    writeToFile(foundWriter, foundExpands, expandsLinesCount)
    requiredWriter.write("================\n")
    requiredWriter.write("=== REQUIRED ===\n")
    requiredWriter.write("================\n")
    writeToFile(requiredWriter, required, linesCount)
    requiredWriter.write("==================\n")
    requiredWriter.write("=== EXPANDS TO ===\n")
    requiredWriter.write("==================\n")
    writeToFile(requiredWriter, requiredExpands, expandsLinesCount)

    foundWriter.close
    requiredWriter.close

    Seq("diff", "-y", requiredFile.getPath, foundFile.getPath).!

    foundFile.delete()
    requiredFile.delete()
  }
}

/*
test:

/Users/abugov/github/abugov/myproject/Main.scala:23:79
type mismatch;
found   : zio.URIO[zio.system.System with zio.logging.Logging with zio.Has[zio.clock.Clock.Service] with zio.Has[zio.console.Console.Service] with zio.Has[zio.system.System.Service] with zio.Has[zio.random.Random.Service] with zio.Has[zio.blocking.Blocking.Service] with zio.console.Console,zio.ExitCode]
(which expands to)  zio.ZIO[zio.Has[zio.system.System.Service] with zio.Has[zio.logging.Logger[String]] with zio.Has[zio.clock.Clock.Service] with zio.Has[zio.console.Console.Service] with zio.Has[zio.system.System.Service] with zio.Has[zio.random.Random.Service] with zio.Has[zio.blocking.Blocking.Service] with zio.Has[zio.console.Console.Service],Nothing,zio.ExitCode]
required: zio.URIO[zio.ZEnv,zio.ExitCode]
(which expands to)  zio.ZIO[zio.Has[zio.clock.Clock.Service] with zio.Has[zio.console.Console.Service] with zio.Has[zio.system.System.Service] with zio.Has[zio.random.Random.Service] with zio.Has[zio.blocking.Blocking.Service],Nothing,zio.ExitCode]
override def run(args: List[String]) = runJobWithMetrics("", program(args)).exitCode


/Users/abugov/github/abugov/myproject/Main.scala:23:79
type mismatch;
 found   : zio.stream.ZStream[zio.blocking.Blocking,io.github.vigoo.zioaws.core.AwsError,Byte]
    (which expands to)  zio.stream.ZStream[zio.Has[zio.blocking.Blocking.Service],io.github.vigoo.zioaws.core.AwsError,Byte]
 required: zio.stream.ZStream[Any,io.github.vigoo.zioaws.core.AwsError,Byte]
    s3.putObject(s3PutObjectRequest, streamWithAwsError)

 */