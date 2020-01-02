package icenet

import chisel3._

object IceNetConsts {
  val NET_IF_WIDTH = 64
  val NET_IF_BYTES = NET_IF_WIDTH/8
  val NET_LEN_BITS = 16

  val ETH_MAX_BYTES = 1520
  val ETH_HEAD_BYTES = 14
  val ETH_MAC_BITS = 48
  val ETH_TYPE_BITS = 16
  val ETH_PAD_BITS = 0 // NOTE(sibanez): this was 16 before ...

  val IPV4_HEAD_BYTES = 20
  val UDP_HEAD_BYTES = 8

  val RLIMIT_MAX_INC = 256
  val RLIMIT_MAX_PERIOD = 256
  val RLIMIT_MAX_SIZE = 256

  def NET_FULL_KEEP = ~0.U(NET_IF_BYTES.W)
  def ETH_BCAST_MAC = ~0.U(ETH_MAC_BITS.W)
}
