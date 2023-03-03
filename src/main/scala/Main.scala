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

  override def run: ZIO[Environment with ZIOAppArgs with Scope, Any, Any] = for {
    input <- ZIO.succeed(pipe())

    // take everything after "found"
    text = input.substring(input.indexOf("found"))

    // expecting 4 lines: found, (which expands to), required, (which expands to)
    /*
    found   : zio.URIO[zio...
       (which expands to)  zio.ZIO[zio...
    required: zio.URIO[zio...
       (which expands to)  zio.ZIO[zio...
     */
    lines = text.split("\n")
    foundStr = cleanFound(lines(0)) // "found ..."
    foundExpandsStr = cleanExpands(lines(1)) // "(which expands to) ..."
    requiredStr = cleanRequired(lines(2)) // "required ..."
    requiredExpandsStr = cleanExpands(lines(3)) // "(which expands to) ..."

    /*
    zio.URIO[zio... , ...]
    zio.ZIO[zio... , ... , ... ]
    zio.URIO[zio... , ...]
    zio.ZIO[zio...  , ... , ... ]
     */

    found = makeZio(foundStr)
    foundExpands = makeZio(foundExpandsStr)
    required = makeZio(requiredStr)
    requiredExpands = makeZio(requiredExpandsStr)

    linesCount = LinesCount(
      math.max(found.R.split('\n').length, required.R.split('\n').length),
      math.max(found.E.split('\n').length, required.E.split('\n').length),
      math.max(found.A.split('\n').length, required.A.split('\n').length)
    )

    expandsLinesCount = LinesCount(
      math.max(foundExpands.R.split('\n').length, requiredExpands.R.split('\n').length),
      math.max(foundExpands.E.split('\n').length, requiredExpands.E.split('\n').length),
      math.max(foundExpands.A.split('\n').length, requiredExpands.A.split('\n').length)
    )

    foundFile = File.createTempFile("ziodiff-", "-found")
    requiredFile = File.createTempFile("ziodiff-", "-required")

    foundWriter = new BufferedWriter(new FileWriter(foundFile))
    requiredWriter = new BufferedWriter(new FileWriter(requiredFile))

    _ <- ZIO.succeed(foundWriter.write("=============\n"))
    _ <- ZIO.succeed(foundWriter.write("=== FOUND ===\n"))
    _ <- ZIO.succeed(foundWriter.write("=============\n"))
    _ <- ZIO.succeed(writeToFile(foundWriter, found, linesCount))
    _ <- ZIO.succeed(foundWriter.write("==================\n"))
    _ <- ZIO.succeed(foundWriter.write("=== EXPANDS TO ===\n"))
    _ <- ZIO.succeed(foundWriter.write("==================\n"))
    _ <- ZIO.succeed(writeToFile(foundWriter, foundExpands, expandsLinesCount))
    _ <- ZIO.succeed(requiredWriter.write("================\n"))
    _ <- ZIO.succeed(requiredWriter.write("=== REQUIRED ===\n"))
    _ <- ZIO.succeed(requiredWriter.write("================\n"))
    _ <- ZIO.succeed(writeToFile(requiredWriter, required, linesCount))
    _ <- ZIO.succeed(requiredWriter.write("==================\n"))
    _ <- ZIO.succeed(requiredWriter.write("=== EXPANDS TO ===\n"))
    _ <- ZIO.succeed(requiredWriter.write("==================\n"))
    _ <- ZIO.succeed(writeToFile(requiredWriter, requiredExpands, expandsLinesCount))

    _ <- ZIO.succeed(foundWriter.close)
    _ <- ZIO.succeed(requiredWriter.close)

    _ <- ZIO.succeed(Seq("diff", "-y", requiredFile.getPath, foundFile.getPath).!)

    _ <- ZIO.succeed(foundFile.delete())
    _ <- ZIO.succeed(requiredFile.delete())
  } yield ()
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
 */