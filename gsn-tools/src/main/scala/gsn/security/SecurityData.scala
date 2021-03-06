package gsn.security

import gsn.data.DataStore
import scala.util.Try
import slick.jdbc.JdbcBackend._
import java.util.UUID

class SecurityData(ds:DataStore) {
  private object Queries{
    val addOrigin=s"""ALTER TABLE acuser 
                    ADD COLUMN ORIGIN VARCHAR(15) NULL;"""
    val addGsnOrigin=s"""UPDATE acuser SET ORIGIN ='GSN';"""
    val testOrigin="SELECT origin from acuser;"
    val userkeys="""CREATE TABLE acuserkey ( 
                    USERNAME VARCHAR(100) NOT NULL,
                    APIKEY VARCHAR(200) NOT NULL,
                    EXPIRES INT,
      				PRIMARY KEY (USERNAME,APIKEY),
      				FOREIGN KEY (USERNAME) REFERENCES ACUSER(USERNAME))"""
    def generateKeys(users:Seq[String])=      
      "INSERT INTO acuserkey VALUES "+
      users.map(u=>s"('$u','${UUID.randomUUID}',null)").mkString(",")
      
    def createUser(username:String,origin:String)=
      s"""INSERT INTO acuser VALUES ('$username' ,'','','','','no','$origin'"""
    def testTable(table:String)=s"SELECT * FROM $table"
   
  }  
  private def existsTable(tableQuery:String)(implicit session:Session)= 
    Try{session.conn.createStatement.execute(tableQuery)}
  
  def upgradeUsersTable:Unit={
    ds.withTransaction{implicit session=>
	  val existOrigin=existsTable(Queries.testOrigin)
	  if (existOrigin.isFailure){
        session.conn.createStatement.execute(Queries.addOrigin)	    
        session.conn.createStatement.execute(Queries.addGsnOrigin)        
	  }	  
	  val existsUserKey=existsTable(Queries.testTable("acuserkey"))
	  if (existsUserKey.isFailure){
        session.conn.createStatement.execute(Queries.userkeys)
        val rs=session.conn.createStatement.executeQuery("SELECT username FROM acuser")
        val users=Iterator.continually(rs.next).takeWhile(a=>a).map(b=>rs.getString(1))
        session.conn.createStatement.execute(Queries.generateKeys(users.toSeq))
	  }
    }
  }
  
  def authorizeVs(vsname:String,apikey:String):Boolean={
    val q=s"""SELECT * FROM acuserkey u,acuser_acdatasource uds WHERE 
    		  datasourcename='$vsname' AND
              u.username=uds.username AND 
    		  u.apikey='$apikey' AND
    		  uds.datasourcetype >0"""
    val qGroup=s"""SELECT * FROM acuserkey u,acgroup_acdatasource gds,acuser_acgroup ug WHERE 
              datasourcename='$vsname' AND 
              u.apikey='$apikey' AND 
              u.username=ug.username AND 
              ug.groupname=gds.groupname AND
              gds.datasourcetype >0"""
    ds.withSession{implicit session=>
      val rs=session.conn.createStatement.executeQuery(q)
      val exists=
        if (rs.next) true
        else {
          val rsGroup=session.conn.createStatement.executeQuery(qGroup)
          rsGroup.next
        }
      exists
    }
  }

  def authorizeVs(vsname:String,user:String,pass:String):Boolean={
    val q=s"""SELECT * FROM acuser u,acuser_acdatasource uds 
              WHERE datasourcename='$vsname' AND 
              u.username='$user' AND u.password='$pass' AND 
              u.username=uds.username"""
    val qGroup=s"""SELECT * FROM acuser u,acgroup_acdatasource gds,acuser_acgroup ug 
              WHERE datasourcename='$vsname' AND 
              u.username='$user' AND u.password='$pass' AND
              u.username=ug.username AND ug.groupname=gds.groupname"""
    ds.withSession{implicit session=>
      val rs=session.conn.createStatement.executeQuery(q)
      val exists=
        if (rs.next) true
        else {
          val rsGroup=session.conn.createStatement.executeQuery(qGroup)
          rsGroup.next
        }
      exists
    }
  }

  def hasAccessControl(resourceName:String)={  
    val q=s"""SELECT * FROM acdatasource ds 
           WHERE ds.datasourcename='${resourceName.toLowerCase}'"""
    ds.withSession{implicit session=>
      val rs=session.conn.createStatement.executeQuery(q)
      rs.next()      
    }
  }
}