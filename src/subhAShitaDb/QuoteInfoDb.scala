package subhAShitaDb

import java.io.File

import sanskritnlp.utils.collectionUtils

import scala.collection.mutable

//import com.couchbase.lite.{Database, Manager, JavaContext, Document, UnsavedRevision, Query, ManagerOptions}
import com.couchbase.lite.{Database, Manager, JavaContext, Document, UnsavedRevision, Query, ManagerOptions}
import com.couchbase.lite.util.Log
import org.json4s._
import org.json4s.native.Serialization
//import org.json4s.jackson.Serialization
import org.slf4j.LoggerFactory
import sanskritnlp.quote.{TopicAnnotation, _}

import scala.collection.JavaConverters._

// This version of the database uses Java (rather than Android) API.
class QuoteInfoDb(language: Language) {
  val log = LoggerFactory.getLogger(getClass.getName)
  var quoteDb: Database = null
  var annotationDb: Database = null
  var dbManager: Manager = null
  def openDatabasesLaptop() = {
    dbManager =  new Manager(new JavaContext("data") {
      override def getRootDirectory: File = {
        val rootDirectoryPath = "/home/vvasuki/subhAShita-db-sanskrit"
        new File(rootDirectoryPath)
      }
    }, Manager.DEFAULT_OPTIONS)
    dbManager.setStorageType("ForestDB")
    quoteDb = dbManager.getDatabase(s"quote_db__${language.code}")
    annotationDb = dbManager.getDatabase(s"annotation_db__${language.code}")
  }

  def closeDatabases = {
    quoteDb.close()
    annotationDb.close()
  }

  def updateDocument(document: Document, jsonMap: Map[String,Object]) = {
    document.update(new Document.DocumentUpdater() {
      override def update(newRevision: UnsavedRevision): Boolean = {
        val properties = newRevision.getUserProperties
        val jsonMapJava = collectionUtils.toJava(jsonMap).asInstanceOf[java.util.Map[String, Object]]
//        log debug jsonMapJava.getClass.toString
        properties.putAll(jsonMapJava)
        newRevision.setUserProperties(properties)
        true
      }
    })
  }

  def addQuote(quoteText: QuoteText) : Boolean = {
    val jsonMap = jsonHelper.getJsonMap(quoteText)
     log debug(jsonMap.toString())
//    sys.exit()
    val document = quoteDb.getDocument(quoteText.key)
    updateDocument(document, jsonMap)
    return true
  }

  def addAnnotation(annotation: Annotation): Boolean = {
    val jsonMap = jsonHelper.getJsonMap(annotation)
//    log debug(annotation.getKey())
    log debug(jsonMap.toString())
    val document = annotationDb.getDocument(annotation.getKey())
    updateDocument(document, jsonMap)
    return true
  }

  // Returns the number of documents updated.
  def addQuoteWithInfo(quoteWithInfo: QuoteWithInfo): Int = {
    var recordsModified = 0
    recordsModified += addQuote(quoteWithInfo.quoteText).compare(false)
    recordsModified += quoteWithInfo.topicAnnotations.map(addAnnotation(_).compare(false)).sum
    recordsModified += quoteWithInfo.descriptionAnnotations.map(addAnnotation(_).compare(false)).sum
    recordsModified += quoteWithInfo.ratingAnnotations.map(addAnnotation(_).compare(false)).sum
    recordsModified += quoteWithInfo.originAnnotations.map(addAnnotation(_).compare(false)).sum
    recordsModified += quoteWithInfo.requestAnnotations.map(addAnnotation(_).compare(false)).sum
    recordsModified += quoteWithInfo.referenceAnnotations.map(addAnnotation(_).compare(false)).sum
    return recordsModified
  }

  def ingestQuoteList(quoteList: QuoteList) = {
    log info s"Updated records ${quoteList.map(addQuoteWithInfo(_)).sum} from ${quoteList.getClass.getSimpleName}"
  }

  def checkConflicts = {
    val query = quoteDb.createAllDocumentsQuery
    query.setAllDocsMode(Query.AllDocsMode.ONLY_CONFLICTS)
    val result = query.run
    result.iterator().asScala.foreach(row => {
      if (row.getConflictingRevisions.size > 0) {
        Log.w("checkConflicts", "Conflict in document: %s", row.getDocumentId)
      }
    })
  }

  def listCaseClassObjects(query: Query) = {
    val result = query.run
    val docObjects = result.iterator().asScala.map(_.getDocument).map(doc => {
      val jsonMap = collectionUtils.toScala(doc.getUserProperties).asInstanceOf[mutable.Map[String, _]]
      //      val jsonMap = doc.getUserProperties
      jsonHelper.fromJsonMap(jsonMap)
    })
    //    log info s"We have ${quotes.length} quotes."
    docObjects.foreach(quoteText => {
      log info quoteText.toString
      log info jsonHelper.getJsonMap(quoteText).toString()
    })
  }

  def listAllCaseClassObjects = {
//    listCaseClassObjects(quoteDb.createAllDocumentsQuery)
    listCaseClassObjects(annotationDb.createAllDocumentsQuery)
  }

  def testQuoteWrite() = {
    val jsonMap =Map("scriptRenderings"-> List(Map("text"-> "दण्डः शास्ति प्रजाः सर्वाः दण्ड एवाभिरक्षति। दण्डः सुप्तेषु जागर्ति दण्डं धर्मं विदुर्बुधाः।।", "scheme" -> "dev", "startLetter" -> "द")),
      "jsonClass"->"QuoteText",
      "language"->Map("code" -> "sa"),
      "key"->"damDaHshaastiprajaaHsarvaaHdamDaevaabhiraxatidamDaHsupteShujaagartidamDamdharmamvidurbudhaaH"
    )
    val document = quoteDb.getDocument(jsonMap("key").toString)
    updateDocument(document, jsonMap)
  }

  def testQuoteRetrieval() = {
    val id = "damDaHshaastiprajaaHsarvaaHdamDaevaabhiraxatidamDaHsupteShujaagartidamDamdharmamvidurbudhaaH"
    val doc = quoteDb.getDocument(id)
    val jsonMap = doc.getUserProperties

    log debug jsonMap.toString
  }

}


object dbMakerSanskrit {
  val log = LoggerFactory.getLogger(getClass.getName)
  val quoteInfoDb = new QuoteInfoDb(language = Language("sa"))

  def testQuoteReadWrite() = {
    quoteInfoDb.openDatabasesLaptop()
    quoteInfoDb.testQuoteWrite()
    quoteInfoDb.testQuoteRetrieval()
    quoteInfoDb.closeDatabases
    quoteInfoDb.openDatabasesLaptop()
    quoteInfoDb.testQuoteRetrieval()
    quoteInfoDb.testQuoteRetrieval()
  }

  def updateDb = {
//    quoteInfoDb.ingestQuoteList(vishvasaPriyaSamskritaPadyani)
    quoteInfoDb.ingestQuoteList(mahAsubhAShitasangraha)
  }


  def main(args: Array[String]): Unit = {
    quoteInfoDb.openDatabasesLaptop()
    // quoteInfoDb.checkConflicts
    updateDb
//    quoteInfoDb.listAllCaseClassObjects
  }
}
