1. Usage
	(a) Go to the src directory, type "make";
	(b) Type "./runMeFirstToGetIp", use that IP for input to set up different clients in the same machine later(for example: 192.168.19.1);
	(c) Type "./bfclient 20000 3"(for example). To start the program. Of course, you can link other nodes by appending "192.168.19.1 20001 3" in the command line. You can append this as many as you like;(Attention: the input ip must be the one specified by (b) if that client running on localhost)
	(d) Open other terminals, follow (c) to start setting up the node in the network.
	(e) Usage of required commands (Case NOT sensitive):
		<1>showrt
		<2>linkdown 192.168.19.1 20001
		<3>linkup 192.168.19.1 20001
		<4>close

2. Protocol
	(a) Route Update 
		<1> 0-6 bytes: flag(UPDATE). Telling what this message is for.
		<2> 6-10 bytes: the size of route table
		<3> following every 50 bytes: one entry of the route table.(client, distance). Link info is stored in route table, but no need to send.
	(b) Link Down
		<1> 0-6 bytes: flag(DOWN). Telling what this message is for.
	(c) Link Up
		<1> 0-6 bytes: flag(UP). Telling what this message is for.
		<2> 10-20 bytes: the original link cost. Used to restore the link cost after linkdown.  
		
3. Unusual (Other commands supported):
	(a) who. Telling what client does this program represents
	(b) shownei. Showing who are neighbors of this local client
	(c) send. Manually send route table to its neighbors
	(d) exit, x. Exit the program.
	
4. Attention: Due to the fact that the client will cut the linkage set initially after 3 timeout without receiving the update message from that neighbors, you would better open several terminals first, and then run it one by one quickly. By doing this, the client wouldn't cut the linkage.