package com.abhioncbr.etlFramework.commons.transform

import com.typesafe.scalalogging.Logger
import org.apache.spark.sql.hive.HiveContext
import org.apache.spark.sql.types.{DataType, StructType}
import org.apache.spark.sql.{DataFrame, Row}

import scala.util.Try
import scala.util.control.NonFatal

object TransformUtil {
  private val logger = Logger(this.getClass)
  lazy val tableMetadata: (String, String, HiveContext, List[String]) =>
    (Option[StructType], Option[StructType]) =
    (tableName: String, databaseName: String, hiveContext: HiveContext, partitionColumns: List[String]) => {

      var tableDataSchema: Option[StructType] = None
      var partitionColSchema: Option[StructType] = None

       //TODO: determine whether table is present in hive or not
       val tableExist: Boolean =true
       //val tableExist: Boolean = hiveContext.sql(s"SHOW TABLES IN $databaseName").map(row => row.get(0)).collect.toSet.contains(tableName)

      if (tableExist) {
        var tableData = hiveContext.sql(s"select * from $databaseName.$tableName limit 1")
        //val tablePartitionInfo = hiveContext.sql(s"SHOW TABLE EXTENDED in $databaseName like $tableName").collect.partition(row => row.getString(0).startsWith("partitionColumns"))._1
        if (partitionColumns.nonEmpty) {
          /*val temp = new scala.util.matching.Regex("(partitionColumns:struct partition_columns [{]{1})([0-9a-z._, ]*)(})", "prefix", "value", "postfix")
          val partitionColumns: Option[List[String]] = tablePartitionInfo.head.getString(0) match {
            case temp(prefix, value, postfix) => Some(value.split(",").map(str => str.trim.split(" ") {
              1
            }.trim).toList)
          }*/

          //if (partitionColumns.isDefined) {
            //selecting only partitioning columns from data frame
            tableData.registerTempTable("temp")
            val partitionColDF = hiveContext.sql(s"select ${partitionColumns.mkString(" , ")} from temp")

            //dropping columns from columns of a table
            partitionColumns.foreach(str => tableData = tableData.drop(str))
            tableDataSchema = Some(tableData.schema)
            partitionColSchema = Some(partitionColDF.schema)
          //}
        }
      }
      (tableDataSchema, partitionColSchema)
    }

  def getColumnsInfo(schema: StructType, mapping: Map[String, String]): List[(Int, String, DataType)] ={
    val tableColumns: List[(Int, String, DataType)] = schema.fields.map(struct => {
      var name = struct.name
      if(mapping.contains(struct.name)) name = mapping.getOrElse(struct.name, "")

      (schema.fieldIndex(struct.name), name, struct.dataType)}).toList
    tableColumns
  }

  def hasColumn(dataFrame: DataFrame, colName: String): Boolean =  {
    dataFrame.select(colName.trim).columns.length == 1
  }

  def selectColumn(row: Row, dataType: DataType, colName: String): Either[Any, String] = {
    var value: Any = null
    try {
      value = dataType match {
        case s: org.apache.spark.sql.types.StringType =>
          val temp = Try(row.getAs[String](colName))
          if(temp.isFailure) {
            row.getAs[Any](colName).toString.trim
          } else temp.get.trim


        case s: org.apache.spark.sql.types.IntegerType =>
          val temp = Try(row.getAs[Int](colName))
          if(temp.isFailure) {
            val temp1 = row.getAs[String](colName).trim
            if (!temp1.isEmpty) {
              val temp2 = Try(temp1.toInt)
              if (temp2.isSuccess) temp2.get
              else return Right(s"$colName value is not Integer type. Value is '$temp1'")
            } else 0
          } else if(temp.get.isInstanceOf[Int]) temp.get
          else 0

        case s: org.apache.spark.sql.types.LongType =>
          val temp = Try(row.getAs[Long](colName))
          if(temp.isFailure) {
            val temp1 = row.getAs[String](colName).trim
            if(!temp1.isEmpty) {
              val temp2 = Try(temp1.toLong)
              if (temp2.isSuccess) temp2.get
              else return Right(s"$colName value is not Long type. Value is '$temp1'")
            } else "0".toLong
          } else if(temp.get.isInstanceOf[Long]) temp.get
          else "0".toLong

        case s: org.apache.spark.sql.types.BooleanType =>
          val temp = Try(row.getAs[Boolean](colName))
          if(temp.isFailure) {
            val temp1 = row.getAs[String](colName).trim
            if(!temp1.isEmpty) {
              val temp2 = Try(temp1.toBoolean)
              if (temp2.isSuccess) temp2.get
              else return Right(s"$colName value is not Boolean type. Value is '$temp1'")
            } else "false".toBoolean
          } else if(temp.get.isInstanceOf[Boolean]) temp.get
          else "false".toBoolean

        case s: org.apache.spark.sql.types.DoubleType =>
          val temp = Try(row.getAs[Double](colName))
          if(temp.isFailure) {
            val temp1 = row.getAs[String](colName).trim
            if(!temp1.isEmpty) {
              val temp2 = Try(temp1.toDouble)
              if (temp2.isSuccess) temp2.get
              else return Right(s"$colName value is not Double type. Value is '$temp1'")
            } else "0.0".toDouble
          } else if(temp.get.isInstanceOf[Double]) temp.get
          else "0.0".toDouble

        case s: org.apache.spark.sql.types.TimestampType =>
          val temp = Try(row.getAs[java.sql.Timestamp](colName))
          if(temp.isFailure) {
            val temp1 = row.getAs[String](colName).trim
            if(!temp1.isEmpty) {
              val temp2 = Try(java.sql.Timestamp.valueOf(temp1))
              if (temp2.isSuccess) temp2.get
              else return Right(s"$colName value is not TimeStamp type. Value is '$temp1'")
            }
          } else temp.get

        case s: org.apache.spark.sql.types.DateType =>
          val temp = Try(row.getAs[java.sql.Date](colName))
          if(temp.isFailure) {
            val temp1 = row.getAs[String](colName).trim
            if(!temp1.isEmpty) {
              val temp2 = Try(java.sql.Date.valueOf(temp1))
              if (temp2.isSuccess) temp2.get
              else return Right(s"$colName value is not Date type. Value is '$temp1'")
            }
          } else temp.get

        case s: org.apache.spark.sql.types.ArrayType => s.elementType match {
          case p: org.apache.spark.sql.types.StructType =>
            val structFields = s.elementType.asInstanceOf[org.apache.spark.sql.types.StructType].fields
            val arrayData = row.getAs[Seq[Row]](colName)
            val temp: Try[Seq[Any]] = Try(arrayData.map(row => {
              val s: Array[Any] = structFields.map(field => {
                val f = selectColumn(row, field.dataType, field.name)
                if (f.isLeft) f.left.get
                else { logger.error("Array Type inner error: " + f.right.get); null}
              })
              if (s.length == 1) s.head
              else Row.fromSeq(s)
            }))
            if(temp.isSuccess) temp.get else return Right(s"Column: $colName, value is not valid Array data type.")
          case _ => row.getAs[Seq[Any]](colName)
        }

        case s: org.apache.spark.sql.types.StructType =>
          val structData = row.getAs[Row](colName)
          val temp1 = Try(Row.fromSeq(s.fields.map(structField => selectColumn(structData, structField.dataType, s"$colName.${structField.name}").left.get).toSeq))
          if(temp1.isSuccess){
            temp1.get
          } else return Right(s"Column: $colName, value is not valid Struct data type. Exception: ${temp1.failed.get.getCause}")

        case default => logger.error(s"column data type is not supported: $default")
          return Right(s"column data type is not supported: $default")
      }
    } catch {
      //not processing any missing field issues, which are caught in below error handling
      case NonFatal(e) => logger.debug(s"column projection $colName, exception: ${e.getMessage}, ${e.getClass}")
    }
    Left(value)
  }

  def flattenStructType(row: Row, preAppend: String): org.apache.spark.sql.Row = {
    if (preAppend.isEmpty) return row
    var outputRow: Row = row
    val temp = preAppend.split("[.]").toList
    temp.foreach(str => outputRow = outputRow.getAs[org.apache.spark.sql.Row](str))
    outputRow
  }
}