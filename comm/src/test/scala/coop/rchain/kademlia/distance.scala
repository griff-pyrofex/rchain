package coop.rchain.kademlia

import org.scalatest._
import scala.util.{Success, Try}
import scala.concurrent.duration.{Duration, MILLISECONDS}

case class PeerNode(val key: Seq[Byte]) extends Peer {
  val rand = new scala.util.Random

  override def ping: Try[Duration] =
    Success(Duration(100, MILLISECONDS))

  override def toString = {
    val skey = key.map("%02x" format _).mkString
    s"#{PeerNode $skey}"
  }
}

object b {
  val rand = new scala.util.Random(System.currentTimeMillis)
  def apply(i: Int): Byte = i.toByte
  def apply(s: String): Byte = b(Integer.parseInt(s, 2))
  def rand(nbytes: Int): Seq[Byte] = {
    val arr = Array.fill(nbytes)(b(0))
    rand.nextBytes(arr)
    arr
  }
}

class DistanceSpec extends FlatSpec with Matchers {
  "A PeerNode of width n bytes" should "have distance to itself equal to 8n" in {
    for (i <- 1 to 64) {
      val home = PeerNode(b.rand(i))
      val nt = PeerTable(home)
      nt.distance(home) should be (Some(8 * nt.width))
    }
  }

  val width = 32

  // Make 8*width copies all of which differ in a single, distinct bit
  def oneOffs(key: Seq[Byte]): Seq[Array[Byte]] =
    for {
      i <- 0 until width
      j <- 7 to 0 by -1
    } yield {
      val k1 = Array.fill(key.size)(b(0))
      Array.copy(k1, 0, key.toArray, 0, key.size)
      k1(i) = b(k1(i) ^ b(1 << j))
      k1
    }

  def testKey(key: Array[Byte]): Boolean = {
    val table = PeerTable(PeerNode(key))
    oneOffs(key).map(table.distance(_)) == ((0 until 8 * width).map(Option[Int](_)))
  }

  def keyString(key: Seq[Byte]): String =
    key.map("%02x".format(_)).mkString

  val k0 = Array.fill(width)(b(0))
  s"A node with key all zeroes (${keyString(k0)})" should "compute distance correctly" in {
    testKey(k0) should be (true)
  }

  val k1 = Array.fill(width)(b(0xff))
  s"A node with key all ones (${keyString(k1)})" should "compute distance correctly" in {
    testKey(k1) should be (true)
  }

  val kr = b.rand(width)
  s"A node with random key (${keyString(kr)})" should "compute distance correctly" in {
    testKey(kr.toArray) should be (true)
  }

  "An empty table" should "have no peers" in {
    val table = PeerTable(PeerNode(kr))
    assert(table.table.forall(_.size == 0))
  }

  it should "return no peers" in {
    val table = PeerTable(PeerNode(kr))
    table.peers.size should be (0)
  }

  it should "return no values on lookup" in {
    val table = PeerTable(PeerNode(kr))
    table.lookup(b.rand(width)).size should be (0)
  }

  "A table" should "add a key at most once" in {
    val table = PeerTable(PeerNode(kr))
    val toAdd = oneOffs(kr).head
    val dist = table.distance(toAdd).get
    for (i <- 1 to 10) {
      table.observe(PeerNode(toAdd))
      table.table(dist).size should be (1)
    }
  }

  "A table with peers at all distances" should "have no empty buckets" in {
    val table = PeerTable(PeerNode(kr))
    for (k <- oneOffs(kr.toArray)) {
      table.observe(PeerNode(k))
    }
    assert(table.table.forall(_.size > 0))
  }

  it should "return k peers on lookup" in {
    val table = PeerTable(PeerNode(kr))
    for (k <- oneOffs(kr.toArray)) {
      table.observe(PeerNode(k))
    }
    table.lookup(b.rand(width)).size should be (table.k)
  }

  it should "not return sought peer on lookup" in {
    val table = PeerTable(PeerNode(kr))
    for (k <- oneOffs(kr.toArray)) {
      table.observe(PeerNode(k))
    }
    val target = table.table(table.width * 4)(0)
    val resp = table.lookup(target.key)
    assert(resp.forall(_.key != target.key))
  }

  it should "return 8n peers when sequenced" in {
    val table = PeerTable(PeerNode(kr))
    for (k <- oneOffs(kr.toArray)) {
      table.observe(PeerNode(k))
    }
    table.peers.size should be (8 * width)
  }

  it should "find each added peer" in {
    val table = PeerTable(PeerNode(kr))
    for (k <- oneOffs(kr.toArray)) {
      table.observe(PeerNode(k))
    }
    for (k <- oneOffs(kr.toArray)) {
      table.find(k) should be (Some(PeerNode(k)))
    }
  }
}
