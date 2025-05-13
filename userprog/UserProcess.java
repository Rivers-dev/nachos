package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import java.util.ArrayList;

import java.io.EOFException;

/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see	nachos.vm.VMProcess
 * @see	nachos.network.NetProcess
 */
public class UserProcess {
    /**
     * Allocate a new process.
     */
    public UserProcess() {
    	procTable = new OpenFile[16];
    	procTable[0] = UserKernel.console.openForReading();
    	procTable[1] = UserKernel.console.openForWriting();
    }
    
    /**
     * Allocate and return a new process of the correct class. The class name
     * is specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     *
     * @return	a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
	return (UserProcess)Lib.constructObject(Machine.getProcessClassName());
    }

    /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {
	if (!load(name, args))
	    return false;
	
	new UThread(this).setName(name).fork();

	return true;
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
	Machine.processor().setPageTable(pageTable);
    }

    /**
     * Read a null-terminated string from this process's virtual memory. Read
     * at most <tt>maxLength + 1</tt> bytes from the specified address, search
     * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
     * without including the null terminator. If no null terminator is found,
     * returns <tt>null</tt>.
     *
     * @param	vaddr	the starting virtual address of the null-terminated
     *			string.
     * @param	maxLength	the maximum number of characters in the string,
     *				not including the null terminator.
     * @return	the string read, or <tt>null</tt> if no null terminator was
     *		found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
	Lib.assertTrue(maxLength >= 0);

	byte[] bytes = new byte[maxLength+1];

	int bytesRead = readVirtualMemory(vaddr, bytes);

	for (int length=0; length<bytesRead; length++) {
	    if (bytes[length] == 0)
		return new String(bytes, 0, length);
	}

	return null;
    }

    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data) {
	return readVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from this process's virtual memory to the specified array.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @param	offset	the first byte to write in the array.
     * @param	length	the number of bytes to transfer from virtual memory to
     *			the array.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data, int offset,
				 int length) {
	Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

	byte[] memory = Machine.processor().getMemory();
	
	if (vaddr < 0 || vaddr + length > virtualMemorySize) // Tests for the virtual address
		return 0;
	
	int amount, nextAmount, pageNum, paddr; // Initailize variables for while loop
	amount = nextAmount = paddr = pageNum = 0;
	
	while (length > 0){
		pageNum = vaddr / pageSize; // find page number
		paddr = (pageTable[pageNum].ppn * pageSize) + (vaddr % pageSize); // generate physical address from pagetable
		
		nextAmount = pageSize - (vaddr % pageSize);
		nextAmount = Math.min(nextAmount, length);
		
		pageTable[pageNum].used = true; // mark page in page table as used
		
		System.arraycopy(memory, paddr, data, offset, nextAmount);
		
		length = length - nextAmount; // adjust variables
		amount = amount + nextAmount;
		vaddr = vaddr + nextAmount;
		offset = offset + nextAmount;
	}
	
	// for now, just assume that virtual addresses equal physical addresses
	/*if (vaddr < 0 || vaddr >= memory.length)
	    return 0;

	int amount = Math.min(length, memory.length-vaddr);
	System.arraycopy(memory, vaddr, data, offset, amount);
	*/

	return amount;
    }

    /**
     * Transfer all data from the specified array to this process's virtual
     * memory.
     * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data) {
	return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from the specified array to this process's virtual memory.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @param	offset	the first byte to transfer from the array.
     * @param	length	the number of bytes to transfer from the array to
     *			virtual memory.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset,
				  int length) {
	Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

	byte[] memory = Machine.processor().getMemory();
	
	int amount, nextAmount, pageNum, paddr;
	amount = nextAmount = pageNum = paddr = 0;
	
	while (length > 0){
		pageNum = vaddr / pageSize; // determine pagenum
		if(pageTable[pageNum].readOnly) // return if we cannot write to page
			return amount;
	
		pageTable[pageNum].dirty = true; // mark page a dirty
		pageTable[pageNum].used = true; // mark page as used
	
		nextAmount = pageSize - (vaddr % pageSize);
		nextAmount = Math.min(nextAmount, length);
		
		length = length - nextAmount;
		paddr = (pageTable[pageNum].ppn * pageSize) + (vaddr % pageSize);
		System.arraycopy(data, offset, memory, paddr, nextAmount);
		amount = amount + nextAmount;
		vaddr  = vaddr  + nextAmount;
		offset = offset + nextAmount;
	}
	
	// for now, just assume that virtual addresses equal physical addresses
	/*if (vaddr < 0 || vaddr >= memory.length)
	    return 0;

	int amount = Math.min(length, memory.length-vaddr);
	System.arraycopy(data, offset, memory, vaddr, amount);*/

	return amount;
    }

    /**
     * Load the executable with the specified name into this process, and
     * prepare to pass it the specified arguments. Opens the executable, reads
     * its header information, and copies sections and arguments into this
     * process's virtual memory.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the executable was successfully loaded.
     */
    private boolean load(String name, String[] args) {
	Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");
	
	OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
	if (executable == null) {
	    Lib.debug(dbgProcess, "\topen failed");
	    return false;
	}

	try {
	    coff = new Coff(executable);
	}
	catch (EOFException e) {
	    executable.close();
	    Lib.debug(dbgProcess, "\tcoff load failed");
	    return false;
	}

	// make sure the sections are contiguous and start at page 0
	numPages = 0;
	for (int s=0; s<coff.getNumSections(); s++) {
	    CoffSection section = coff.getSection(s);
	    if (section.getFirstVPN() != numPages) {
		coff.close();
		Lib.debug(dbgProcess, "\tfragmented executable");
		return false;
	    }
	    numPages += section.getLength();
	}

	// make sure the argv array will fit in one page
	byte[][] argv = new byte[args.length][];
	int argsSize = 0;
	for (int i=0; i<args.length; i++) {
	    argv[i] = args[i].getBytes();
	    // 4 bytes for argv[] pointer; then string plus one for null byte
	    argsSize += 4 + argv[i].length + 1;
	}
	if (argsSize > pageSize) {
	    coff.close();
	    Lib.debug(dbgProcess, "\targuments too long");
	    return false;
	}

	// program counter initially points at the program entry point
	initialPC = coff.getEntryPoint();	

	// next comes the stack; stack pointer initially points to top of it
	numPages += stackPages;
	initialSP = numPages*pageSize;

	// and finally reserve 1 page for arguments
	numPages++;

	if (!loadSections())
	    return false;

	// store arguments in last page
	int entryOffset = (numPages-1)*pageSize;
	int stringOffset = entryOffset + args.length*4;

	this.argc = args.length;
	this.argv = entryOffset;
	
	for (int i=0; i<argv.length; i++) {
	    byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
	    Lib.assertTrue(writeVirtualMemory(entryOffset,stringOffsetBytes) == 4);
	    entryOffset += 4;
	    Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) ==
		       argv[i].length);
	    stringOffset += argv[i].length;
	    Lib.assertTrue(writeVirtualMemory(stringOffset,new byte[] { 0 }) == 1);
	    stringOffset += 1;
	}

	return true;
    }

    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be
     * run (this is the last step in process initialization that can fail).
     *
     * @return	<tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections() {
    	
	if (numPages > Machine.processor().getNumPhysPages() || numPages > UserKernel.freeFrames.size()) { //added freeFrames check
	    coff.close();
	    return false;
	}
	
	pageTable = new TranslationEntry[numPages];
	for (int x = 0; x < numPages; x++){
		pageTable[x] = (TranslationEntry)UserKernel.freeFrames.removeFirst();
		pageTable[x].valid = true;
		pageTable[x].vpn = x;
	}
	
	virtualMemorySize = numPages * pageSize;
	int x = 0;
			
	// load sections
	for (int s = 0; s < coff.getNumSections(); s++) {
	    CoffSection section = coff.getSection(s);
	   
	    for (int i = 0; i < section.getLength(); i++) {
	    	int ppn = pageTable[x].ppn;
	    	x++;
	    	section.loadPage(i, ppn);
	    	if(section.isReadOnly())
	    		pageTable[i].readOnly = true;
	    	

	    	/*/ for now, just assume virtual addresses=physical addresses
	    	section.loadPage(i, vpn);*/
	    }
	}
	
	return true;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
    	for (int i = 0; i < pageTable.length; i++) {
    		pageTable[i].valid = pageTable[i].readOnly = pageTable[i].used = pageTable[i].dirty = false;
    	   	UserKernel.freeFrames.add(pageTable[i]);
    	   	pageTable[i] = null;
		}
    }    

    /**
     * Initialize the processor's registers in preparation for running the
     * program loaded into this process. Set the PC register to point at the
     * start function, set the stack pointer register to point at the top of
     * the stack, set the A0 and A1 registers to argc and argv, respectively,
     * and initialize all other registers to 0.
     */
    public void initRegisters() {
	Processor processor = Machine.processor();

	// by default, everything's 0
	for (int i=0; i<processor.numUserRegisters; i++)
	    processor.writeRegister(i, 0);

	// initialize PC and SP according
	processor.writeRegister(Processor.regPC, initialPC);
	processor.writeRegister(Processor.regSP, initialSP);

	// initialize the first two argument registers to argc and argv
	processor.writeRegister(Processor.regA0, argc);
	processor.writeRegister(Processor.regA1, argv);
    }

    /**
     * Handle the halt() system call. 
     */
    private int handleHalt() {
        System.out.println("LOG: Inside handleHalt"); // added in class
    	if (processID > 0){
    		return -1;
    	}

    	Machine.halt();

    	Lib.assertNotReached("Machine.halt() did not halt machine!");
    	return 0;
    }

    public void handleExit(int status){
		System.out.println("LOG: Inside handleExit"); 

        // TODO: if time permits
    	return;
    }

    public int handleExec(int fileDescriptor, int argc, int argv){
        System.out.println("LOG: Inside handleExec");

        // sanity check
        if (fileDescriptor < 0 || argc < 0 || argv < 0) return -1;
    	
    	String name = this.readVirtualMemoryString(fileDescriptor, dSize);
    	String[] args = new String[argc];
    	byte[] data = new byte[(args.length) * 4];
    	int numberRead = readVirtualMemory(argv, data);
    	
        if (name == null | numberRead < data.length) return -1;
    	
    	for (int i = 0; i < data.length / 4; i++) {
    		args[i] = readVirtualMemoryString(Lib.bytesToInt(data, i * 4), dSize);
   			if (args[i] == null) return -1;
    	}

    	OpenFile e = ThreadedKernel.fileSystem.open(name, false);
       	if (e == null) return -1;
    	e.close();

      	UserProcess newProc = UserProcess.newUserProcess();
       	if (newProc.execute(name, args) == false) return -1;  

   		children.add(newProc);
   		newProc.parent = (UThread) UThread.currentThread();
    	
    	return newProc.processID;
    }

    public int handleJoin(int processID, int status){
        System.out.println("LOG: Inside handleJoin");

    	boolean stat = Machine.interrupt().disable();
    	UserProcess curProc = null;

		for (UserProcess child : children)
		{
			if (child.processID == processID) {
				curProc = child;
				break;
			}
		}	

	 	if(curProc == null) return -1;
	 	
	   	curProc.waiting = (UThread)UThread.currentThread();
	   	
    	UThread.currentThread().sleep();
    	children.remove(curProc);
   					 						
    	byte[] array = new byte[4];
  		Lib.bytesFromInt(array, 0, curProc.exitStatus);
  		int write = writeVirtualMemory(status, array);
  		
   		Machine.interrupt().restore(stat); 
   		
  		if (write != array.length) return -1;
 		else if (curProc.exitStatus == 0) return 1;
  		
        // uh oh
  		return 0;
    }

    public int handleCreate(int a0){
    	if (a0 < 0)
    		return -1;
    	
    	String fileName = this.readVirtualMemoryString(a0, dSize);
    	if (fileName == null)
    		return -1;
    	
    	OpenFile newFile = ThreadedKernel.fileSystem.open(fileName, true);
    	if(newFile == null)
    		return -1;
    	
    	int index = nextIdx();
    	if (index != -1){
    		procTable[index] = newFile;
    		return index;
    	}
    	
    	return -1;
    }
    
    public int handleOpen(int a0){
    	String fileName = this.readVirtualMemoryString(a0, dSize);
    	if (fileName == null) return -1;
    	
    	OpenFile newFile = ThreadedKernel.fileSystem.open(fileName, false);
    	if (newFile == null) return -1;
    	
    	int index = nextIdx();
    	if (index != -1) {
    		procTable[index] = newFile;
    		return index;
    	}

    	return -1;
    }


    public int handleUnlink(int a0){
    	String fileName = readVirtualMemoryString(a0, dSize);
		if (fileName == null) return -1;

		OpenFile f = ThreadedKernel.fileSystem.open(fileName, false);
		if (f != null) {
			f.close();

			if (ThreadedKernel.fileSystem.remove(fileName)) return 0;
		}
		
		return -1;
    }


    public int handleClose(int a0){
    	if ((a0 >= 0) && (a0 < procTable.length) && (procTable[a0] != null)) {
				procTable[a0].close(); 		
				procTable[a0] = null;
				return 0;
	    	}
	    return -1;
    }

    public int handleWrite(int fileDescriptor, int buffLocation, int length){
		// sanity
    	if(fileDescriptor < 0 || fileDescriptor >= procTable.length || buffLocation < 0) return -1;
    	
		OpenFile file = procTable[fileDescriptor];
		if (file == null) return -1;
		
		byte[] buffer= new byte[length];
		int readAtLoc = readVirtualMemory(buffLocation, buffer, 0, length);
		int res = file.write(buffer, 0, readAtLoc);

		if(res != 0 ) return res;	

		return -1;
    }

    public int handleRead(int fileDescriptor, int buffLocation, int length){
    	if(fileDescriptor < 0 || fileDescriptor >= procTable.length || buffLocation < 0 || length < 0) return -1;

    	OpenFile file = procTable[fileDescriptor];
        if (file == null) return -1;

        byte[] buff = new byte[length];
        int sizeRead = file.read(buff, 0, length);

        writeVirtualMemory(buffLocation, buff);

        return sizeRead;
	}

    private static final int
    syscallHalt = 0,
	syscallExit = 1,
	syscallExec = 2,
	syscallJoin = 3,
	syscallCreate = 4,
	syscallOpen = 5,
	syscallRead = 6,
	syscallWrite = 7,
	syscallClose = 8,
	syscallUnlink = 9;

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr><td>syscall#</td><td>syscall prototype</td></tr>
     * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
     * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
     * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
     * 								</tt></td></tr>
     * <tr><td>3</td><td><tt>int  join(int pid, int *status);</tt></td></tr>
     * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
     * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
     * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
     * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
     * </table>
     * 
     * @param	syscall	the syscall number.
     * @param	a0	the first syscall argument.
     * @param	a1	the second syscall argument.
     * @param	a2	the third syscall argument.
     * @param	a3	the fourth syscall argument.
     * @return	the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
	switch (syscall) {
		case syscallHalt:
	    	return handleHalt();
        case syscallExit:
			handleExit(a0);
        case syscallExec:
			return handleExec(a0, a1, a2);
        case syscallJoin:
			return handleJoin(a0, a1);
        case syscallCreate:
			return handleCreate(a0);
		case syscallOpen:
			return handleOpen(a0);
        case syscallRead:
			return handleRead(a0, a1, a2);
		case syscallWrite:
			return handleWrite(a0, a1, a2);
        case syscallClose:
			return handleClose(a0);
        case syscallUnlink:
			return handleUnlink(a0);
		default:
	    	Lib.debug(dbgProcess, "Unknown syscall " + syscall);
	    	Lib.assertNotReached("Unknown system call!");
		}

		return 0;
    }

    /**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The
     * <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param	cause	the user exception that occurred.
     */
    public void handleException(int cause) {
	Processor processor = Machine.processor();

	switch (cause) {
	case Processor.exceptionSyscall:
	    int result = handleSyscall(processor.readRegister(Processor.regV0),
				       processor.readRegister(Processor.regA0),
				       processor.readRegister(Processor.regA1),
				       processor.readRegister(Processor.regA2),
				       processor.readRegister(Processor.regA3)
				       );
	    processor.writeRegister(Processor.regV0, result);
	    processor.advancePC();
	    break;				       
				       
	default:
	    Lib.debug(dbgProcess, "Unexpected exception: " +
		      Processor.exceptionNames[cause]);
	    Lib.assertNotReached("Unexpected exception");
	}
    }

    
    
    /** The program being run by this process. */
    protected Coff coff;

    /** This process's page table. */
    protected TranslationEntry[] pageTable;
    /** The number of contiguous pages occupied by the program. */
    protected int numPages;

    /** The number of pages in the program's stack. */
    protected final int stackPages = 8;
    
    private int initialPC, initialSP;
    private int argc, argv;
	
    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';
    
    private final int processID = ++UserProcess.gID; // given in class
    private ArrayList<UserProcess> children = new ArrayList<UserProcess>(); // given in class
    public KThread parent, waiting;
    private OpenFile[] procTable; // given in class
    private int exitStatus;
    
    private static int gID = -1;
    protected int virtualMemorySize;
    public final int dSize = 256;

	public int nextIdx() {
    	for (int i = 0; i < procTable.length; i++) {
			if (procTable[i] == null)
				return i;
		}

    	return -1;
    }
}
