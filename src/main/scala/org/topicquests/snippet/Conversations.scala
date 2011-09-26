/**
 *
 */
package org.topicquests.snippet

import net.liftweb.util._
import net.liftweb.common._
import net.liftweb.util.Helpers._
import net.liftweb.http._
import js.JE.JsRaw
import js.JsCmd
import net.liftweb.mapper._
import net.liftweb.http.SHtml.ChoiceHolder
import net.liftweb.http.S._
import org.topicquests.model._
import scala.xml._
import scala.collection.immutable.List
import java.util.Date
import org.topicquests.comet.{ConversationCometServer, AddNodeAction}
import net.liftweb.http.js.JsCmds._
import org.topicquests.model.NodeModel
import js.jquery.JqJE

/**
 * @author park
 *
 */


/**
 * 	load a conversation from database
 * @param id
 */
case class ConversationLoc(id: String) {
  lazy val record: IBISConversation = {
    IBISConversation.find(By(IBISConversation.id, id.toLong)).open_!
  }
}

/**
 * Fetch a particular node
 * @param id
 */
case class IBISNodeLoc(id: Long) {
  lazy val record: org.topicquests.model.Node = {
    org.topicquests.model.Node.find(By(org.topicquests.model.Node.id, id)).open_!
  }
}

class Conversations extends Loggable {

  //used for Node creation
  var model: NodeModel = new NodeModel()

  /**Creates the bindings for the edit node form
    * @param in  the xml to be transformed
   * @return the xml transformed
   *
   */
  def updatenode(in: NodeSeq) : NodeSeq = {
    var label = ""
    var details = ""
    var nodetype = ""
    // set in Conversation.show
    var x = "-1"

    var parentId: Long = -1
    try {
      //this will fail when the user is not logged in
      //but in that case, we are not painting the response form anyway
      x = S.get("nodeid").openTheBox
      parentId = x.toLong
    } catch {
      case exc : Exception => {
        //do nothing
      }
    }
    // grab the node
    var thenode: org.topicquests.model.Node = IBISNodeLoc(parentId).record
    nodetype = thenode.nodetype.toString()
    label = thenode.label.toString()
    details = thenode.details.toString()

    def process(): JsCmd = {
      logger.info("STARTING PARENT ID "+x+" "+nodetype)
      User.currentUser match {
        case Full(user) =>  {
          model.updateNode(nodetype,label,details, user, thenode)
          S.notice("Edit saved")
          JqJE.Jq("#node_" + thenode.uniqueId + " > .nodehref > .nodetitle") ~> JqJE.JqHtml(Text(label))
        }
        case _ => Noop;
      }
    }

    val radmap = Seq[String] ("Question", "Idea", "Pro", "Con", "Reference")
    val radios: ChoiceHolder[String] = SHtml.radio(radmap,
      Full(nodetype),
      nodetype = _ )

    SHtml.ajaxForm(
      bind("editentry", in,
        "question" -> radios(0),
        "idea" -> radios(1),
        "pro" -> radios(2),
        "con" -> radios(3),
        "ref" -> radios(4),
        "label" -> SHtml.textarea(label, label = _ , "class" -> "required","style" -> "width:auto;height:auto;","cols" -> "79","rows" -> "2","id" -> "editlabel"),
        "details" -> SHtml.textarea(details,  details = _ , "cols" -> "79", "rows" -> "5", "id" -> "editdetails"),
        "submit" -> SHtml.ajaxSubmit("Save", process)
        )
      )    }


  /**Creates the bindings for the response node form
    * @param in  the xml to be transformed
   * @return the xml transformed
   *
   */
  def addresponse(in: NodeSeq) : NodeSeq = {
    var label = ""
    var details = ""
    var nodetype = ""
    // set in Conversation.show
    var x = "-1"

    var parentId: Long = -1

    def process(): JsCmd = {
      try {
        //this will fail when the user is not logged in
        //but in that case, we are not painting the response form anyway
        x = S.get("nodeid").openTheBox
        parentId = x.toLong
      } catch {
        case exc : Exception => {
          //do nothing
        }
      }
      logger.info("STARTING PARENT ID "+x+" "+nodetype)
      User.currentUser match {
        case Full(user) =>  {
          doRespond(nodetype,label,details,parentId, user)
          S.notice("Response saved")
          //Clears the inputs
          JsRaw("$('#resplabel').val('')").cmd & JsRaw("$('#respdetails').val('')")
        }
        case _ => Noop;
      }
    }

    val radmap = Seq[String] ("Question", "Idea", "Pro", "Con", "Reference")
    val radios: ChoiceHolder[String] = SHtml.radio(radmap,
      Full("Question"),
      nodetype = _ )

    SHtml.ajaxForm(
      bind("entry", in,
        "question" -> radios(0),
        "idea" -> radios(1),
        "pro" -> radios(2),
        "con" -> radios(3),
        "ref" -> radios(4),
        "label" -> SHtml.textarea(label, label = _ , "class" -> "required","style" -> "width:auto;height:auto;","cols" -> "79","rows" -> "2","id" -> "resplabel"),
        "details" -> SHtml.textarea(details,  details = _ , "cols" -> "79", "rows" -> "5", "id" -> "respdetails"),
        "submit" -> SHtml.ajaxSubmit("Respond", process)
        )
      )
  }

  /**
   * Driven by a form on the front page for New Conversation
   *
   * @param in  the xml to be transformed
   * @return the xml transformed
   */
  def addnewconversation(in: NodeSeq) : NodeSeq = {

    var title = ""
    var label = ""
    var details = ""
    var nodetype = ""

    def process() {
      logger.info("STARTING")
      User.currentUser match {
        case Full(user) =>  doAdd(title,nodetype,label,details,user)
        case _ => Text("No user available")

      }
      S.redirectTo("/")
    }


    val radmap = Map("Idea"->"I", "Question"->"Q") //this could have been a Seq[String]
    val radios: ChoiceHolder[String] = SHtml.radio(radmap.keys.toList,
      Full("Question"),
      nodetype = _ )
    bind("entry", in,
      "title" -> SHtml.text(title, title = _, "class" -> "required"),
      "question" -> radios(1),
      "idea" -> radios(0),
      "label" -> SHtml.textarea(label,
        label = _ ,
        "class" -> "required",
        "style" -> "width:auto;height:auto;",
        "cols" -> "68",
        "rows" -> "2"),
      "details" -> SHtml.textarea(details,
        details = _ ,
        "style" -> "width:auto;",
        "cols" -> "68",
        "rows" -> "5"),
      "submit" -> SHtml.submit("Update", process)
      )

  }

  /**
   * Here when a user responds to a node, called from <code>addresponse</code>
   * @param nodetype  the type of the node
   * @param label     the label of the node
   * @param details   the details of the node
   * @param parentId  the id of the parent node
   * @param user      the user who created it
   */
  def doRespond(nodetype: String, label: String, details: String, parentId: Long, user: User) = {
    logger.info("PROCESSING "+label+" "+nodetype+" "+parentId)
    var date: Date = new Date()
    var node: org.topicquests.model.Node = model.createNode(nodetype,label,details,user)
    var parentUniqueId = ""
    if (parentId > -1) {
      var nx = IBISNodeLoc(parentId).record
      //saves the parent node uniqueId
      parentUniqueId = nx.uniqueId.is
      //set node's parent
      node.parent(nx)
      //set's the conversation
      node.conversation(nx.conversation.obj)
      //add child to parent
      var snappers: List[org.topicquests.model.Node] = nx.children.toList
      //These printlins do, indeed, show the node has children
      //but PROCESSING-BB doesn't show the added child.
      //you see it in the next PROCESSING-AA -- when another child is added
      //NOT SURE WHY THAT HAPPENS
      //logger.info("PROCESSING-AA "+snappers)
      snappers :+ node
      // logger.info("PROCESSING-BB "+snappers)
      nx.save()
      //	  logger.info("PROCESSING-XX "+nx)
    }
    //still need to know how this plays out
    node.save()

    //Updates all comets listeners
    ConversationCometServer ! new AddNodeAction(parentId, parentUniqueId, node)
  }

  /**
   * Heavy lifting of creating an IBISConversation
   *
   * @param title the title of the node
   * @param nodetype the type of the node
   * @param label the label of the node
   * @param details the details of the node
   * @param user  the user who created it
   *
   */
  def doAdd(title: String, nodetype: String, label: String, details: String, user: User) = {
    logger.info("PROCESSING "+title+" "+nodetype)
    //make the root node
    var date: Date = new Date()
    var node: org.topicquests.model.Node = model.createNode(nodetype,label,details,user)
    node.save()
    logger.info("PROCESSING-2 "+node)
    //make the conversation itself
    //technically speaking, this is really a Map node
    //TODO upgrade from Conversation to Map nodetype
    var conversation: IBISConversation = IBISConversation.create
    conversation.label(title)
    conversation.creator(user)
    conversation.startdate(date)
    conversation.lastdate(date)
    conversation.rootnodeid(node)
    conversation.save()
    logger.info("PROCESSED "+conversation.id)

    //Adds the conversation to the node object
    node.conversation(conversation).save()
  }

  /**
   * List all IBISConversations as HREFs for the front page
   * TODO: paging if the list gets too long
   */
  def list(in: NodeSeq) = {
    val convs: List[IBISConversation] = IBISConversation.findAll()
    //    logger.info("XXXX "+convs)
    bindMessages(convs,in)
  }

  /**
   * Convert a list of IBISConversations into a list of HREFs to those
   * conversations
   */
  def bindMessages(messageList: List[IBISConversation],in: NodeSeq): NodeSeq = {
    messageList.flatMap{m =>
      val ix = m.id.toString()
      val lbl = m.label.toString()
      <a href={"/conversation/"+ix}>{lbl}</a><br />
    }
  }

}