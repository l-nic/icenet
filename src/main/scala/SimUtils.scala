package icenet

import Chisel._

import chisel3.util.{HasBlackBoxResource}
import chisel3.experimental._
import freechips.rocketchip.rocket._
import freechips.rocketchip.config.Parameters
import testchipip.{StreamIO, StreamChannel}
import NICIO._
import NICIOvonly._

// class StreamChannel(val w: Int) extends Bundle {
//   val data = UInt(w.W)
//   val keep = UInt((w/8).W)
//   val last = Bool()

//   override def cloneType = new StreamChannel(w).asInstanceOf[this.type]
// }

// class StreamIO(w: Int) extends Bundle {
//   val in = Flipped(Decoupled(new StreamChannel(w)))
//   val out = Decoupled(new StreamChannel(w))

//   def flipConnect(other: StreamIO) {
//     in <> other.out
//     other.in <> out
//   }

//   override def cloneType = new StreamIO(w).asInstanceOf[this.type]
// }

class StreamIOvonly(w: Int) extends Bundle {
  val in = Flipped(Valid(new StreamChannel(w)))
  val out = Valid(new StreamChannel(w))

  def flipConnect(other: StreamIOvonly) {
    in <> other.out
    other.in <> out
  }

  override def cloneType = new StreamIOvonly(w).asInstanceOf[this.type]
}

// object NetworkHelpers {
//   def reverse_bytes(a: UInt, n: Int) = {
//     val bytes = (0 until n).map(i => a((i + 1) * 8 - 1, i * 8))
//     Cat(bytes)
//   }
// }

object LNICConsts {
  val IPV4_TYPE = "h0800".U(16.W)
  val LNIC_PROTO = "h99".U(8.W)
  val LNIC_CONTEXT_BITS = 16
  val NET_IF_BITS = 64
  // NOTE: these are only used for the Simulation Timestamp/Latency measurement module
  val TEST_CONTEXT_ID = 0x1234.U(LNIC_CONTEXT_BITS.W)
}

@chiselName
class LatencyModule extends Module {
  val io = IO(new Bundle {
    val net = new StreamIOvonly(LNICConsts.NET_IF_BITS) 
    val nic = new StreamIOvonly(LNICConsts.NET_IF_BITS)
  })

  val nic_ts = Module(new Timestamp(to_nic = true))
  nic_ts.io.net.in <> io.net.in
  io.nic.out <> nic_ts.io.net.out

  val net_ts = Module(new Timestamp(to_nic = false))
  net_ts.io.net.in <> io.nic.in
  io.net.out <> net_ts.io.net.out
}

@chiselName
class Timestamp(to_nic: Boolean = true) extends Module {
  val io = IO(new Bundle {
   val net = new StreamIOvonly(LNICConsts.NET_IF_BITS)
  })

  val net_word = Reg(Valid(new StreamChannel(LNICConsts.NET_IF_BITS)))

  net_word.valid := io.net.in.valid
  io.net.out.valid := net_word.valid // default
  net_word.bits := io.net.in.bits
  io.net.out.bits := net_word.bits

  // state machine to parse headers
  val sWordOne :: sWordTwo :: sWordThree :: sWordFour :: sWordFive :: sWordSix :: sWaitEnd :: Nil = Enum(7)
  val state = RegInit(sWordOne)

  val reg_now = RegInit(0.U(32.W))
  reg_now := reg_now + 1.U

  val reg_ts_start = RegInit(0.U)
  val reg_eth_type = RegInit(0.U)
  val reg_ip_proto = RegInit(0.U)
  val reg_lnic_flags = RegInit(0.U)
  val reg_lnic_src = RegInit(0.U)
  val reg_lnic_dst = RegInit(0.U)

  switch (state) {
    is (sWordOne) {
      reg_ts_start := reg_now
      transition(sWordTwo)
    }
    is (sWordTwo) {
      reg_eth_type := NetworkHelpers.reverse_bytes(net_word.bits.data(47, 32), 2)
      transition(sWordThree)
    }
    is (sWordThree) {
      reg_ip_proto := net_word.bits.data(63, 56)
      transition(sWordFour)
    }
    is (sWordFour) {
      transition(sWordFive)
    }
    is (sWordFive) {
      reg_lnic_flags := net_word.bits.data(23, 16)
      reg_lnic_src := NetworkHelpers.reverse_bytes(net_word.bits.data(39, 24), 2)
      reg_lnic_dst := NetworkHelpers.reverse_bytes(net_word.bits.data(55, 40), 2)
      transition(sWordSix)
    }
    is (sWordSix) {
      transition(sWaitEnd)
    }
    is (sWaitEnd) {
      when (net_word.valid && net_word.bits.last) {
        state := sWordOne
        // overwrite last bytes with timestamp / latency
        val is_lnic_data = Wire(Bool())
        is_lnic_data := reg_eth_type === LNICConsts.IPV4_TYPE && reg_ip_proto === LNICConsts.LNIC_PROTO && reg_lnic_flags(0).asBool
        when (is_lnic_data) {
          val insert_timestamp = Wire(Bool())
          insert_timestamp:= to_nic.B && (reg_lnic_src === LNICConsts.TEST_CONTEXT_ID) 
          val insert_latency = Wire(Bool())
          insert_latency := !to_nic.B && (reg_lnic_dst === LNICConsts.TEST_CONTEXT_ID)
          val new_data = Wire(UInt())
          when (insert_timestamp) {
            new_data := Cat(NetworkHelpers.reverse_bytes(reg_ts_start, 4), net_word.bits.data(31, 0))
            io.net.out.bits.data := new_data
            io.net.out.bits.keep := net_word.bits.keep
            io.net.out.bits.last := net_word.bits.last
          } .elsewhen (insert_latency) {
            val pkt_ts = NetworkHelpers.reverse_bytes(net_word.bits.data(63, 32), 4)
            new_data := Cat(NetworkHelpers.reverse_bytes(reg_now - pkt_ts, 4), NetworkHelpers.reverse_bytes(reg_now, 4))
            // last 4B is latency, first 4B is timestamp
            io.net.out.bits.data := new_data
            io.net.out.bits.keep := net_word.bits.keep
            io.net.out.bits.last := net_word.bits.last
          }
        }
      }
    }
  }

  def transition(next_state: UInt) = {
    when (net_word.valid) {
      when (!net_word.bits.last) {
        state := next_state
      } .otherwise {
        state := sWordOne
      }
    }
  }

}