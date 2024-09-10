import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.stream.ActorMaterializer
import euclid.sdk.models.DataPacket
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn

// JSON serialization
case class DataRequest(data: String, dataType: String, encryptionKey: String, privacyLevel: String)
object DataRequestJsonProtocol extends DefaultJsonProtocol {
  implicit val dataRequestFormat = jsonFormat4(DataRequest)
}

object ApiServer {
  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem("databridge")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher

    import DataRequestJsonProtocol._

    val route =
      path("submitData") {
        post {
          entity(as[String]) { json =>
            val dataRequest = json.parseJson.convertTo[DataRequest]
            
            // Convert to SecureDataPacket and validate
            val packet = SecureDataPacket(
              dataRequest.data, 
              dataRequest.dataType, 
              dataRequest.encryptionKey, 
              PrivacySettings(dataRequest.privacyLevel)
            )

            val validationResult = DataBridge.validateData(packet)
            if (validationResult.valid) {
              complete(StatusCodes.OK, "Data submitted successfully!")
            } else {
              complete(StatusCodes.BadRequest, validationResult.reason)
            }
          }
        }
      }

    // Start the HTTP server
    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // Let it run until user presses return
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  }
}
