
import "DPI-C" function void network_tick (
    input string devname,

    input  bit      tx_valid,
    output bit      tx_ready,
    input  longint  tx_data,
    input  byte     tx_keep,
    input  bit      tx_last,

    output bit      rx_valid,
    input  bit      rx_ready,
    output longint  rx_data,
    output byte     rx_keep,
    output bit      rx_last,

    output longint  nic_mac_addr
);

import "DPI-C" function void network_init (
    input string    devname,
    input longint   nic_mac_addr
);

module SimNetwork #(
  parameter DEVNAME = "tap0"
)
(
    input             clock,
    input             reset,

    // TX packets: HW --> tap iface
    input             net_out_valid,
    input  [63:0]     net_out_bits_data,
    input  [7:0]      net_out_bits_keep,
    input             net_out_bits_last,

    // RX packets: tap iface --> HW
    output reg        net_in_valid,
    output reg [63:0] net_in_bits_data,
    output reg [7:0]  net_in_bits_keep,
    output reg        net_in_bits_last,

    output [47:0]     net_macAddr,
    // rate limiter settings
    output [7:0]      net_rlimit_inc,
    output [7:0]      net_rlimit_period,
    output [7:0]      net_rlimit_size,
    // pauser settings - NOTE: currently unused
    output [15:0]     net_pauser_threshold,
    output [15:0]     net_pauser_quanta,
    output [15:0]     net_pauser_refresh
);

    string devname = DEVNAME;
    longint nic_mac_addr = 0;

    // NOTE: currently unused
    reg net_out_ready;
    int dummy;

    longint _nic_mac_addr;
    reg [63:0] _nic_mac_addr_reg;

    initial begin
        dummy = $value$plusargs("nic_mac_addr=%h", nic_mac_addr);
        network_init(devname, nic_mac_addr);
    end

    // NOTE: rlimit and pauser settings are currently unused
    assign net_rlimit_inc = 1;
    assign net_rlimit_period = 1;
    assign net_rlimit_size = 8;
    assign net_pauser_threshold = 0;
    assign net_pauser_quanta = 0;
    assign net_pauser_refresh = 0;

    always@(posedge clock) begin
        if (reset) begin
            net_out_ready = 0;
            net_in_valid = 0;
            net_in_bits_data = 0;
            net_in_bits_keep = 0;
            net_in_bits_last = 0;
        end
        else begin
            network_tick(
                devname,

                net_out_valid,
                net_out_ready,
                net_out_bits_data,
                net_out_bits_keep,
                net_out_bits_last,
    
                net_in_valid,
                1'b1,
                net_in_bits_data,
                net_in_bits_keep,
                net_in_bits_last,
                
                _nic_mac_addr);
            _nic_mac_addr_reg <= _nic_mac_addr;
        end
    end

    assign net_macAddr = _nic_mac_addr_reg[47:0];

endmodule
