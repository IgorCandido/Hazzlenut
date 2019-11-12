package hazzlenut.services.twitch.model


import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import cats.implicits._
import spray.json.{DefaultJsonProtocol, JsValue, RootJsonFormat, _}

import scala.reflect.ClassTag

case class TwitchReply[T](total: Option[Long],
                          data: Option[List[T]],
                          pagination: Option[Pagination])
case class Pagination(cursor: String)

object Pagination extends SprayJsonSupport with DefaultJsonProtocol {
  implicit def PaginationFormat = jsonFormat1(Pagination.apply)
}

object TwitchReply extends SprayJsonSupport with DefaultJsonProtocol {
  implicit class SeqHelper[B, C](val seq: Seq[(B, C)]) extends AnyVal{
    def addToSeq[T](seqElements: Seq[(B, C)], name: B, value: Option[T], f: T => C): Seq[(B, C)] =
      value match {
        case Some(v) => seqElements :+ name -> f(v)
        case None => seqElements
      }

    def addElement[T](name: B, value: Option[T], f: T => C): Seq[(B, C)] =
      addToSeq[T](seq, name, value, f)
  }

  implicit def twitchReplyFormat[T](
    implicit formaterT: RootJsonFormat[T]
  ): RootJsonFormat[TwitchReply[T]] = new RootJsonFormat[TwitchReply[T]] {
    val fieldNames = Seq("total", "pagination", "data")

    def transformJsArray[A](
      jsValue: JsValue
    )(implicit fmt: RootJsonFormat[A]): Option[List[A]] =
      jsValue match {
        case JsArray(values) => values.map(_.convertTo[A]).toList.some
        case _               => None
      }

    override def read(json: JsValue): TwitchReply[T] = {
      val values = json.asJsObject.fields.filter({
        _ match {
          case (name, _) if fieldNames.contains(name) => true
          case _                                      => false
        }
      })

      TwitchReply(
        values.get("total").map(_.convertTo[Long]),
        values.get("data").flatMap(transformJsArray[T]),
        values.get("pagination").map(_.convertTo[Pagination])
      )

    }



    override def write(obj: TwitchReply[T]): JsValue =
      JsObject(Seq.empty[(String,JsValue)]
        .addElement[Long]("total", obj.total, t => JsNumber(t))
        .addElement[List[T]]("data", obj.data, t => JsArray(t.map(formaterT.write(_)):_*) )
        .addElement[Pagination]("pagination", obj.pagination, t => implicitly[RootJsonFormat[Pagination]].write(t)): _*)
  }
}
