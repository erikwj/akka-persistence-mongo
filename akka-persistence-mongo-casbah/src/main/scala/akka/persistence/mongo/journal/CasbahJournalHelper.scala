/**
 *  Copyright (C) 2013-2014 Duncan DeVore. <http://reactant.org>
 */
package akka.persistence.mongo.journal

import akka.persistence.PersistentRepr

import com.mongodb.casbah.Imports._

import akka.persistence.mongo.MongoPersistenceJournalRoot

private[mongo] trait CasbahJournalHelper extends MongoPersistenceJournalRoot {

  val PersistenceIdKey = "persistenceId"
  val SequenceNrKey = "sequenceNr"
  val AggIdKey = "_id"
  val AddDetailsKey = "details"
  val MarkerKey = "marker"
  val MessageKey = "message"
  val MarkerAccepted = "A"
  val MarkerConfirmPrefix = "C"
  def markerConfirm(cId: String) = s"C-$cId"
  def markerConfirmParsePrefix(cId: String) = cId.substring(0,1)
  def markerConfirmParseSuffix(cId: String) = cId.substring(2)
  val MarkerDelete = "D"

  private[this] val idx1 = MongoDBObject(
    "persistenceId"         -> 1,
    "sequenceNr"          -> 1,
    "marker"              -> 1)

  private[this] val idx1Options =
    MongoDBObject("unique" -> true)

  private[this] val idx2 = MongoDBObject(
    "persistenceId"         -> 1,
    "sequenceNr"          -> 1)

  private[this] val idx3 =
    MongoDBObject("sequenceNr" -> 1)

  private[this] val uri = MongoClientURI(configMongoJournalUrl)
  val client =  MongoClient(uri)
  private[this] val db = client(uri.database.get)
  val collection = db(uri.collection.get)

  collection.ensureIndex(idx1, idx1Options)
  collection.ensureIndex(idx2)
  collection.ensureIndex(idx3)

  def writeJSON(pId: String, sNr: Long, pr: PersistentRepr) = {
    val builder = MongoDBObject.newBuilder
    builder += PersistenceIdKey -> pId
    builder += SequenceNrKey  -> sNr
    builder += MarkerKey      -> MarkerAccepted
    builder += MessageKey     -> toBytes(pr)
    builder.result()
  }

  def confirmJSON(pId: String, sNr: Long, cId: String) = {
    val builder = MongoDBObject.newBuilder
    builder += PersistenceIdKey -> pId
    builder += SequenceNrKey  -> sNr
    builder += MarkerKey      -> markerConfirm(cId)
    builder += MessageKey     -> Array.empty[Byte]
    builder.result()
  }

  def deleteMarkJSON(pId: String, sNr: Long) = {
    val builder = MongoDBObject.newBuilder
    builder += PersistenceIdKey -> pId
    builder += SequenceNrKey  -> sNr
    builder += MarkerKey      -> MarkerDelete
    builder += MessageKey     -> Array.empty[Byte]
    builder.result()
  }

  def delStatement(persistenceId: String, sequenceNr: Long): MongoDBObject =
    MongoDBObject(PersistenceIdKey -> persistenceId, SequenceNrKey -> sequenceNr)

  def delToStatement(persistenceId: String, toSequenceNr: Long): MongoDBObject =
    MongoDBObject(
      PersistenceIdKey -> persistenceId,
      SequenceNrKey  -> MongoDBObject("$lte" -> toSequenceNr))

  def delOrStatement(elements: List[MongoDBObject]): MongoDBObject =
    MongoDBObject("$or" -> elements)

  def replayFindStatement(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long): MongoDBObject =
    MongoDBObject(
      PersistenceIdKey -> persistenceId,
      SequenceNrKey  -> MongoDBObject("$gte" -> fromSequenceNr, "$lte" -> toSequenceNr))

  def recoverySortStatement = MongoDBObject(
    "persistenceId" -> 1,
    "sequenceNr"  -> 1,
    "marker"      -> 1)

  def snrQueryStatement(persistenceId: String): MongoDBObject =
    MongoDBObject(PersistenceIdKey -> persistenceId)

  def maxSnrSortStatement: MongoDBObject =
    MongoDBObject(SequenceNrKey -> -1)

  def minSnrSortStatement: MongoDBObject =
    MongoDBObject(SequenceNrKey -> 1)

  def casbahJournalWriteConcern: WriteConcern = configMongoJournalWriteConcern
}
