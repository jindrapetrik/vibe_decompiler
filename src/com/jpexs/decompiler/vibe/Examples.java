package com.jpexs.decompiler.vibe;

import com.jpexs.decompiler.vibe.structure.IfStructure;
import com.jpexs.decompiler.vibe.structure.LoopStructure;
import com.jpexs.decompiler.vibe.structure.LabeledBlockStructure;
import com.jpexs.decompiler.vibe.structure.TryStructure;

/**
 * Contains example demonstrations for the StructureDetector.
 *
 * @author JPEXS
 */
public class Examples {
    
    private static void runExample(String description, String dot) {
        System.out.println("===== " + description + " =====");
        StructureDetector detector = StructureDetector.fromGraphviz(dot);
        detector.analyze();
        System.out.println("\n--- Pseudocode ---");
        System.out.println(detector.toPseudocode());
        System.out.println("--- Graphviz/DOT ---");
        System.out.println(detector.toGraphviz());
    }
    
    /**
     * Runs an example with the given DOT source and description, also printing detected structures.
     * 
     * @param description the description of the example
     * @param dot the DOT/Graphviz source code
     * @param printStructures if true, prints detected if, loop, and labeled block structures
     */
    private static void runExample(String description, String dot, boolean printStructures) {
        System.out.println("===== " + description + " =====");
        StructureDetector detector = StructureDetector.fromGraphviz(dot);
        detector.analyze();
        
        if (printStructures) {
            System.out.println("\n--- Detected Structures ---");
            System.out.println("If Structures: " + detector.detectIfs().size());
            for (IfStructure s : detector.detectIfs()) {
                System.out.println("  " + s);
            }
            System.out.println("Loop Structures: " + detector.detectLoops().size());
            for (LoopStructure s : detector.detectLoops()) {
                System.out.println("  " + s);
            }
            System.out.println("Labeled Block Structures: " + detector.getLabeledBlocks().size());
            for (LabeledBlockStructure s : detector.getLabeledBlocks()) {
                System.out.println("  " + s);
            }
        }
        
        System.out.println("\n--- Pseudocode ---");
        System.out.println(detector.toPseudocode());
        System.out.println("--- Graphviz/DOT ---");
        System.out.println(detector.toGraphviz());
    }
    
    /**
     * Runs an example with the given DOT source, description, and exception definitions.
     * Creates a StructureDetector, parses exceptions, analyzes it, and prints pseudocode.
     * 
     * @param description the description of the example
     * @param dot the DOT/Graphviz source code
     * @param exceptions the exception definition string (format: "tryNodes => catchNodes; ...")
     */
    private static void runExampleWithExceptions(String description, String dot, String exceptions) {
        System.out.println("===== " + description + " =====");
        StructureDetector detector = StructureDetector.fromGraphviz(dot);
        detector.parseExceptions(exceptions);
        detector.analyze();
        
        System.out.println("\n--- Detected Structures ---");
        System.out.println("If Structures: " + detector.detectIfs().size());
        for (IfStructure s : detector.detectIfs()) {
            System.out.println("  " + s);
        }
        System.out.println("Try Structures: " + detector.getTryStructures().size());
        for (TryStructure s : detector.getTryStructures()) {
            System.out.println("  " + s);
        }
        
        System.out.println("\n--- Pseudocode ---");
        System.out.println(detector.toPseudocode());
        System.out.println("--- Graphviz/DOT ---");
        System.out.println(detector.toGraphviz());
    }

    /**
     * Demonstration with example CFGs.
     */
    public static void main(String[] args) {
        // Example 1: Simple if-else
        runExample("Example 1: Simple If-Else",
            "digraph {\n" +
            "  entry->if_cond;\n" +
            "  if_cond->else;\n" +
            "  if_cond->then;\n" +
            "  then->merge;\n" +
            "  else->merge;\n" +
            "  merge->exit;\n" +
            "}"
        );
        /* 
        Expected output:
        
            entry;
            if (!if_cond) {
                then;
            } else {
                else;
            }
            merge;
            exit;
        */
        
        // Example 2: While loop
        System.out.println();
        runExample("Example 2: While Loop",
            "digraph {\n" +
            "  entry->loop_header;\n" +
            "  loop_header->exit;\n" +
            "  loop_header->loop_body;\n" +
            "  loop_body->loop_header;\n" +
            "}"
        );
        /* 
        Expected output:
            entry;
            while(true) {
                if(loop_header) {
                    break;
                }
                loop_body;
            }
            exit;
        */
        
        // Example 3: Loop with break and continue
        System.out.println();
        runExample("Example 3: Loop with Break and Continue",
            "digraph {\n" +
            "  entry->loop_header;\n" +
            "  loop_header->exit;\n" +
            "  loop_header->body_1;\n" +
            "  body_1->cond_break;\n" +
            "  cond_break->exit;\n" +
            "  cond_break->body_2;\n" +
            "  body_2->cond_continue;\n" +
            "  cond_continue->loop_header;\n" +
            "  cond_continue->body_3;\n" +
            "  body_3->loop_header;\n" +
            "}"
        );        
        /*
        Expected output:
        
        entry;
        while(true) {
            if (loop_header) {
                break;
            }
            body_1;
            if (cond_break) {
                break;
            }
            body_2;
            if (cond_continue) {
                continue;
            }
            body_3;
        }
        exit;
        */
        
        // Example 4: Nested loops
        System.out.println();
        runExample("Example 4: Nested Loops",
            "digraph {\n" +
            "  entry->outer_header;\n" +
            "  outer_header->exit;\n" +
            "  outer_header->inner_header;\n" +
            "  inner_header->outer_end;\n" +
            "  inner_header->inner_body;\n" +
            "  inner_body->inner_header;\n" +
            "  outer_end->outer_header;\n" +
            "}"
        );
        /*
        Expected output:
        
        entry;
        while(true) {
            if (outer_header) {
                break;
            }
            while(true) {
                if (inner_header) {
                    break;
                }
                inner_body;
            }
            outer_end;
        }
        exit;
        */
        
        // Example 5: If inside loop
        System.out.println();
        runExample("Example 5: If Inside Loop",
            "digraph {\n" +
            "  entry->loop_header;\n" +
            "  loop_header->exit;\n" +
            "  loop_header->if_cond;\n" +
            "  if_cond->if_else;\n" +
            "  if_cond->if_then;\n" +
            "  if_then->loop_end;\n" +
            "  if_else->loop_end;\n" +
            "  loop_end->loop_header;\n" +
            "}"
        );
        /*
        Expected output:
        
        entry;
        while(true) {
            if (loop_header) {
                break;
            }
            if (!if_cond) {
                if_then;
            } else {
                if_else;
            }
            loop_end;
        }
        exit;        
        */
        
        
        // Example 6: Chained edges (demonstrating a->b->c syntax)
        System.out.println();
        runExample("Example 6: Chained Edges",
            "digraph {\n" +
            "  start->if1;\n" +
            "  if1->onfalse;\n" +
            "  if1->ontrue;\n" +
            "  ontrue->merge;\n" +
            "  onfalse->merge;\n" +
            "  merge->after->exit;\n" +
            "}"
        );
        /*
        Expected output:
        
        start;
        if (!if1) {
            ontrue;
        } else {
            onfalse;
        }
        merge;
        after;
        exit;
        */
        
        // Example 7: Complex nested loops with labeled breaks and returns
        System.out.println();
        runExample("Example 7: Complex Nested Loops with Labeled Breaks",
            "digraph {\n" +
            "  entry->loop_a_cond;\n" +
            "  loop_a_cond->loop_b_cond;\n" +
            "  loop_a_cond->exit;\n" +
            "  loop_b_cond->inner_cond;\n" +
            "  loop_b_cond->trace_hello;\n" +
            "  inner_cond->check_e9;\n" +
            "  inner_cond->inc_d;\n" +
            "  trace_hello->my_cont;\n" +
            "  check_e9->trace_X;\n" +
            "  check_e9->ifr;\n" +
            "  ifr->check_e20;\n" +
            "  ifr->r1;\n" +
            "  trace_X->trace_hello;\n" +
            "  check_e20->trace_Y;\n" +
            "  check_e20->ifr2;\n" +
            "  ifr2->check_e8;\n" +
            "  ifr2->r2;\n" +
            "  trace_Y->my_cont;\n" +
            "  check_e8->trace_Z;\n" +
            "  check_e8->trace_BA;\n" +
            "  trace_Z->inc_d;\n" +
            "  trace_BA->exit;\n" +
            "  inc_d->loop_b_cond;\n" +
            "  my_cont->ternar;\n" +
            "  ternar->pre_inc_b;\n" +
            "  ternar->pre_inc_a;\n" +
            "  pre_inc_a->inc_c;\n" +
            "  pre_inc_b->inc_c;\n" +
            "  inc_c->loop_a_cond;\n" +
            "}"
        );
        /*
        Expected output:
        
        entry;
        block_0: {
            loop_1: while(true) {
                if (!loop_a_cond) {
                    break;
                }
                block_2: {
                    loop_3: while(true) {
                        if (!loop_b_cond) {
                            break;
                        }
                        block_4: {
                            if (!inner_cond) {
                                break;
                            }
                            if (check_e9) {
                                trace_X;
                                break loop_3;
                            }
                            if (!ifr) {
                                r1;
                                break block_0;
                            }
                            if (check_e20) {
                                trace_Y;
                                break block_2;
                            }
                            if (!ifr2) {
                                r2;
                                break block_0;
                            }
                            if (!check_e8) {
                                trace_BA;
                                break loop_1;
                            }
                            trace_Z;                                    
                        }
                        inc_d;
                    }
                    trace_hello;
                }
                my_cont;
                if (!ternar) {
                    pre_inc_a;                    
                } else {
                    pre_inc_b;
                }
                inc_c;
            }
            exit;
        }
        */
        
        // Example 8: Do-while style loop (body first, then condition)
        System.out.println();
        runExample("Example 8: Do-While Style Loop",
            "digraph {\n" +
            "  entry->body;\n" +
            "  body->cond;\n" +
            "  cond->exit;\n" +
            "  cond->body;\n" +
            "}"
        );
        /*
        Expected output:
        
        entry;
        while(true) {
            body;
            if (cond) {
                break;
            }
        }
        exit;
        */        

        // Example 9: Labeled block with nested if-statements
        System.out.println();
        runExample("Example 9: Labeled Block with Nested Ifs",
            "digraph {\n" +
            "  start->ifa;\n" +
            "  ifa->x;\n" +
            "  ifa->A1;\n" +
            "  x->ifc;\n" +
            "  ifc->y;\n" +
            "  ifc->z;\n" +
            "  y->A2;\n" +
            "  z->A1;\n" +
            "  A1->d;\n" +
            "  d->A2;\n" +
            "  A2->end;\n" +
            "}",
            true
        );
        /*
        Expected output:
        
        start;
        block_0: {
            if (ifa) {
                x;
                if (ifc) {
                    y;
                    break;
                }
                z;
            }
            A1;
            d;
        }
        A2;
        end;
        */

        // Example 10: Two nested while loops with labeled blocks inside
        System.out.println();
        runExample("Example 10: Nested Ifs with While Loop",
            "digraph {\n" +
            "  start->ifa;\n" +
            "  ifa->x;\n" +
            "  ifa->A1;\n" +
            "  x->ifc;\n" +
            "  A1->d;\n" +
            "  ifc->y;\n" +
            "  ifc->z;\n" +
            "  d->A2;\n" +
            "  y->A2;\n" +
            "  z->A1;\n" +
            "  A2->start2;\n" +
            "  start2->ifex2;\n" +
            "  ifex2->end;\n" +
            "  ifex2->ifa2;\n" +
            "  ifa2->x2;\n" +
            "  ifa2->A12;\n" +
            "  x2->ifc2;\n" +
            "  A12->d2;\n" +
            "  ifc2->y2;\n" +
            "  ifc2->z2;\n" +
            "  d2->A22;\n" +
            "  y2->A22;\n" +
            "  z2->A12;\n" +
            "  A22->start3;\n" +
            "  start3->ifex3;\n" +
            "  ifex3->end;\n" +
            "  ifex3->ifa3;\n" +
            "  ifa3->x3;\n" +
            "  ifa3->A13;\n" +
            "  x3->ifc3;\n" +
            "  A13->d3;\n" +
            "  ifc3->y3;\n" +
            "  ifc3->z3;\n" +
            "  d3->A23;\n" +
            "  y3->A23;\n" +
            "  z3->A13;\n" +
            "  A23->start2;\n" +
            "}",
            true
        );
        /*
        Expected output:
        
        start;
        block_0: {
            if (ifa) {
                x;
                if (ifc) {
                    y;
                    break;
                }
                z;
            }
            A1;
            d;
        }
        A2;
        loop_1: while(true) {
            start2;
            block_2: {
                if (ifex2) {
                    break loop_1;
                }
                if (ifa2) {
                    x2;
                    if (ifc2) {
                        y2;
                        break;
                    }
                    z2;
                }
                A12;
                d2;
            }
            A22;
            start3;
            block_3: {
                if (ifex3) {
                    break loop_1;
                }
                if (ifa3) {
                    x3;
                    if (ifc3) {
                        y3;
                        break;
                    }
                    z3;
                }
                A13;
                d3;
            }
            A23;
        }
        end;
        */

        // Example 11: Switch-like chain of conditions
        System.out.println();
        runExample("Example 11: Switch-like Chain of Conditions",
            "digraph {\n" +
            "  start->if1;\n" +
            "  if1->case1;\n" +
            "  if2->case2;\n" +
            "  if3->case3;\n" +
            "  if4->case4;\n" +
            "  if1->if2;\n" +
            "  if2->if3;\n" +
            "  if3->if4;\n" +
            "  if4->d;\n" +
            "  case1->end;\n" +
            "  case2->end;\n" +
            "  case3->end;\n" +
            "  case4->end;\n" +
            "  d->end;\n" +
            "  if1[_operator=\"===\"];\n" +
            "  if2[_operator=\"===\"];\n" +
            "  if3[_operator=\"===\"];\n" +
            "  if4[_operator=\"===\"];\n" +            
            "}",
            true
        );
        /*
        Expected output:
         
        start;
        if (!if1) {
            if (!if2) {
                if (!if3) {
                    if (!if4) {
                        d;
                    } else {
                        case4;
                    }
                } else {
                    case3;
                }
            } else {
                case2;
            }
        } else {
            case1;
        }
        end;
        */
        
        /*
        Code with switches:
        
        start;
        switch {
            case if1:
                case1;
                break;
            case if2:
                case2;
                break;
            case if3:
                case3;
                break;
            case if4:
                case4;
                break;
            default:
                d;                
        }
        end;
        */

        // Example 12: Switch with fall-through, merged cases, and nested if-else
        System.out.println();
        runExample("Example 12: Switch with Fall-through and Merged Cases",
            "digraph {\n" +
            "  start->if1;\n" +
            "  if1->case1;\n" +
            "  if2->case2;\n" +
            "  if3->case3;\n" +
            "  if4->case45;\n" +
            "  if5->case45;\n" +
            "  if1->if2;\n" +
            "  if2->if3;\n" +
            "  if3->if4;\n" +
            "  if4->if5;\n" +
            "  if5->d;\n" +
            "  case1->n;\n" +
            "  n->a;\n" +
            "  n->b;\n" +
            "  a->c;\n" +
            "  b->c;\n" +
            "  c->end;\n" +
            "  case3->end;\n" +
            "  case45->end;\n" +
            "  d->end;\n" +
            "  case2->case3;\n" +
            "  if1[_operator=\"===\"];\n" +
            "  if2[_operator=\"===\"];\n" +
            "  if3[_operator=\"===\"];\n" +
            "  if4[_operator=\"===\"];\n" +            
            "  if5[_operator=\"===\"];\n" +                        
            "}",
            true
        );
        /*
        Expected output:
        
        start;
        block_0: {
            if (!if1) {
                if (!if2) {
                    if (!if3) {
                        if (!if4) {
                            if (!if5) {
                                d;
                                break;
                            }
                        }
                        case45;
                        break;
                    }
                } else {
                    case2;
                }
                case3;
            } else {
                case1;
                if (!n) {
                    b;
                } else {
                    a;
                }
                c;
            }
        }
        end;
        */
        
        /*
        Code with switches:
        
        start;
        switch {
            case if1:
                case1;
                if(n) {
                    a;
                } else {
                    b;
                }
                c;
                break;
            case if2:
                case2;
            case if3:
                case3;
                break;
            case if4:
            case if5:
                case45;
                break;
            default:
                d;        
        }
        end;
        */

        // Example 13: Loop with switch inside and labeled break/continue
        System.out.println();
        runExample("Example 13: Loop with Switch, Labeled Break and Continue",
            "digraph {\n" +
            "  start->cond;\n" +
            "  cond->end;\n" +
            "  cond->if1;\n" +
            "  if1->case1;\n" +
            "  if2->case2;\n" +
            "  if3->case3;\n" +
            "  if4->case45;\n" +
            "  if5->case45;\n" +
            "  if1->if2;\n" +
            "  if2->if3;\n" +
            "  if3->if4;\n" +
            "  if4->if5;\n" +
            "  if5->d;\n" +
            "  case1->n;\n" +
            "  n->a;\n" +
            "  n->b;\n" +
            "  a->c;\n" +
            "  b->c;\n" +
            "  c->m;\n" +
            "  m->end;\n" +
            "  m->t;\n" +
            "  t->v;\n" +
            "  v->x;\n" +
            "  x->cond;\n" +
            "  v->w;\n" +
            "  w->sw_end;\n" +
            "  case3->sw_end;\n" +
            "  case45->sw_end;\n" +
            "  d->sw_end;\n" +
            "  sw_end->cond;\n" +
            "  case2->case3;\n" +
            "  if1[_operator=\"===\"];\n" +
            "  if2[_operator=\"===\"];\n" +
            "  if3[_operator=\"===\"];\n" +
            "  if4[_operator=\"===\"];\n" +            
            "  if5[_operator=\"===\"];\n" +  
            "}",
            true
        );
        /*
        Expected output:
        
        start;
        loop_0: while(true) {
            if (cond) {
                break;
            }
            block_1: {
                if (!if1) {
                    if (!if2) {
                        if (!if3) {
                            if (!if4) {
                                if (!if5) {
                                    d;
                                    break;
                                }
                            }
                            case45;
                            break;
                        }
                    } else {
                        case2;
                    }
                    case3;
                } else {
                    case1;
                    if(!n) {
                        b;
                    } else {
                        a;
                    }           
                    c;
                    if (m) {
                       break loop_0;
                    }
                    t;
                    if (v) {
                        x;
                        continue loop_0;
                    }
                    w;
                }
            }
            sw_end;
        }
        end;
        */
        
        /*
        Code with switches:
        
        start;
        loop_0: while(true) {
            if (cond) {
                break;
            }
            switch {
                case if1:
                    case1;
                    if (n) {
                        a;
                    } else {
                        b;
                    }
                    c;
                    if (m) {
                        break loop_0;
                    }
                    t;
                    if (v) {
                        x;
                        continue loop_0;
                    }
                    w;
                    break;
                case if2:
                    case2;
                case if3:
                    case3;
                    break;
                case if4:
                case if5:
                    case45;
                    break;
                default:
                    d;                    
            }
            sw_end;
        }
        end;
        */

        // Example 14: Try-Catch with if-else in both blocks
        System.out.println();
        runExampleWithExceptions("Example 14: Try-Catch with If-Else",
            "digraph {\n" +
            "  start->trybody1;\n" +
            "  trybody1->a;\n" +
            "  trybody1->b;\n" +
            "  a->trybody2;\n" +
            "  b->trybody2;\n" +
            "  trybody2->end;\n" +
            "  catchbody1->c;\n" +
            "  catchbody1->d;\n" +
            "  c->catchbody2;\n" +
            "  d->catchbody2;\n" +
            "  catchbody2->end;\n" +
            "}",
            "trybody1, a, b, trybody2 => catchbody1, c, d, catchbody2"
        );
        /*
        Expected output:
        
        start;
        try {
            if (trybody1) {
                a;
            } else {
                b;
            }
            trybody2;
        } catch(0) {
            if (catchbody1) {
                c;
            } else {
                d;
            }
            catchbody2;
        }
        end;
        */

        // Example 15: Nested Try-Catch blocks
        System.out.println();
        runExampleWithExceptions("Example 15: Nested Try-Catch",
            "digraph {\n" +
            "  start->before_try2;\n" +
            "  before_try2->trybody1;\n" +
            "  trybody1->a;\n" +
            "  trybody1->b;\n" +
            "  a->trybody2;\n" +
            "  b->trybody2;\n" +
            "  trybody2->after_try2;\n" +
            "  catchbody1->c;\n" +
            "  catchbody1->d;\n" +
            "  c->catchbody2;\n" +
            "  d->catchbody2;\n" +
            "  catchbody2->after_try2;\n" +
            "  after_try2->end;\n" +
            "  catchbody3->end;\n" +
            "}",
            "before_try2, trybody1, a, b, trybody2, catchbody1, c, d, catchbody2, after_try2 => catchbody3; " +
            "trybody1, a, b, trybody2 => catchbody1, c, d, catchbody2"
        );
        /*
        Expected output:
        
        start;
        try {
            before_try2;
            try {
                if (trybody1) {
                    a;
                } else {
                    b;
                }
                trybody2;
            } catch(1) {
                if (catchbody1) {
                    c;
                } else {
                    d;
                }
                catchbody2;
            }
            after_try2;
        } catch(0) {
            catchbody3;
        }
        end;
        */
        
        
        // Example 16: Multiple Catch Blocks (same try body, multiple handlers)
        System.out.println();
        runExampleWithExceptions("Example 16: Multiple Catch Blocks",
            "digraph {\n" +
            "  start->trybody1;\n" +
            "  trybody1->a;\n" +
            "  trybody1->b;\n" +
            "  a->trybody2;\n" +
            "  b->trybody2;\n" +
            "  trybody2->end;\n" +
            "  catchbody1->c;\n" +
            "  catchbody1->d;\n" +
            "  c->catchbody2;\n" +
            "  d->catchbody2;\n" +
            "  catchbody2->end;\n" +
            "  catchbody3->end;\n" +
            "}",
            "trybody1, a, b, trybody2 => catchbody1, c, d, catchbody2; " +
            "trybody1, a, b, trybody2 => catchbody3"
        );       
        /*
        Expected output:
        
        start;
        try {
            if (trybody1) {
                a;
            } else {
                b;
            }
            trybody2;
        } catch(0) {
            if (catchbody1) {
                c;
            } else {
                d;
            }
            catchbody2;
        } catch(1) {
            catchbody3;
        }
        end;
        */

        // Example 17: Try-Catch inside While Loop with Break and Continue
        System.out.println();
        runExampleWithExceptions("Example 17: Try-Catch in While Loop with Break and Continue",
            "digraph {\n" +
            "  start;\n" +
            "  start->cond;\n" +
            "  cond->end;\n" +
            "  cond->a;\n" +
            "  c1->end;\n" +
            "  c1->d;\n" +
            "  d->H;\n" +
            "  d->k;\n" +
            "  H->k;\n" +
            "  H->H;\n" +
            "  k->after_try;\n" +
            "  c2->cond;\n" +
            "  a->after_try;\n" +
            "  after_try->cond;\n" +
            "  end;\n" +
            "}",
            "a => c1, d, H, k; " +
            "a => c2"
        );
        /*
        Expected output:
        
        start;
        while(true) {
            if (cond) {
                break;
            }
            try {
                a;
            } catch(0) {
                if (c1) {
                    break;
                }
                if (d) {
                    while(true) {
                        if (H) {
                            break;
                        }
                    }
                }
                k;
            } catch(1) {
                c2;
                continue;
            }
            after_try;
        }
        end;
        */        

        // Example 18: Switch with labeled break from inner while loop
        System.out.println();
        runExample("Example 18: Switch with Labeled Break from Inner While Loop",
            "digraph {\n" +
            "  start->if1;\n" +
            "  if1->case1;\n" +
            "  if2->case2;\n" +
            "  if3->case3;\n" +
            "  if4->case4;\n" +
            "  if1->if2;\n" +
            "  if2->if3;\n" +
            "  if3->if4;\n" +
            "  if4->d;\n" +
            "  case1->end;\n" +
            "  case2->h;\n" +
            "  h->i;\n" +
            "  h->g;\n" +
            "  g->end;\n" +
            "  g->h;\n" +
            "  i->end;\n" +
            "  case3->end;\n" +
            "  case4->end;\n" +
            "  d->end;\n" +
            "  if1[_operator=\"===\"];\n" +
            "  if2[_operator=\"===\"];\n" +
            "  if3[_operator=\"===\"];\n" +
            "  if4[_operator=\"===\"];\n" +
            "}",
            true
        );
        /*
        Expected output:
        
        start;
        block_0: {
            if (!if1) {
                if (!if2) {
                    if (!if3) {
                        if (!if4) {
                            d;
                        } else {
                            case4;
                        }
                    } else {
                        case3;
                    }
                } else {
                    case2;
                    while(true) {
                        if (h) {
                            break;
                        }
                        if (g) {
                            break block_0;
                        }
                    }
                    i;
                }
            } else {
                case1;
            }
        }
        end;
        */
        
        /*
        Code with switches:
        
        start;
        loop_0:switch {
            case if1:
                case1;
                break;
            case if2:
                case2;
                loop_1: while(true) {
                    if (h) {
                        break;
                    }
                    if (g) {
                        break loop_0;
                    }
                }
                i;
                break;
            case if3:
                case3;
                break;
            case if4:
                case4;
                break;
            default:
                d;                
        }
        end;
        */

        // Example 19: Two nested while loops with labeled blocks inside and breaks to different loop levels
        System.out.println();
        runExample("Example 19: Nested While Loops with Multi-Level Breaks",
            "digraph {\n" +
            "  start->start1;\n" +
            "  start1->start2;\n" +
            "  start2->ifex2;\n" +
            "  ifex2->end1;\n" +
            "  ifex2->ifa2;\n" +
            "  ifa2->x2;\n" +
            "  ifa2->A12;\n" +
            "  x2->ifc2;\n" +
            "  A12->d2;\n" +
            "  ifc2->y2;\n" +
            "  ifc2->z2;\n" +
            "  d2->A22;\n" +
            "  y2->A22;\n" +
            "  z2->A12;\n" +
            "  A22->start3;\n" +
            "  start3->ifex3;\n" +
            "  ifex3->end1;\n" +
            "  ifex3->ifex4;\n" +
            "  ifex4->end;\n" +
            "  ifex4->ifa3;\n" +
            "  ifa3->x3;\n" +
            "  ifa3->A13;\n" +
            "  x3->ifc3;\n" +
            "  A13->d3;\n" +
            "  ifc3->y3;\n" +
            "  ifc3->z3;\n" +
            "  d3->A23;\n" +
            "  y3->A23;\n" +
            "  z3->A13;\n" +
            "  A23->start2;\n" +
            "  end1->start1;\n" +
            "}",
            true
        );
        /*
        Expected output:
        
        start;
        loop_0: while(true) {
            start1;
            loop_1: while(true) {
                start2;
                block_2: {
                    if (ifex2) {
                        break loop_1;
                    }
                    if (ifa2) {
                        x2;
                        if (ifc2) {
                            y2;
                            break;
                        }
                        z2;
                    }
                    A12;
                    d2;
                }
                A22;
                start3;
                block_3: {
                    if (ifex3) {
                        break loop_1;
                    }
                    if (ifex4) {
                        break loop_0;
                    }
                    if (ifa3) {
                        x3;
                        if (ifc3) {
                            y3;
                            break;
                        }
                        z3;
                    }
                    A13;
                    d3;
                }
                A23;
            }
            end1;
        }
        end;
        */
        
        // Example 20: Simple if no else
        runExample("Example 20: Simple If no else",
            "digraph {\n" +
            "  entry->if_cond;\n" +
            "  if_cond->merge;\n" +
            "  if_cond->then;\n" +
            "  then->merge;\n" +
            "  merge->exit;\n" +
            "}"
        );
        /*
        Expected output:
        
        entry;
        if (!if_cond) {
            then;
        }
        merge;
        exit;
        */
        
        // Example 21: Nested ifs in loop with continue and break
        runExample("Example 21: Nested ifs in loop with continue and break",
            "digraph {\n" +
            "  vara->hdr;" +
            "  hdr->end;" +
            "  hdr->ifa9;\n"+
            "  ifa9->hel2;\n" +
            "  ifa9->ifa8;\n" +
            "  ifa8->ifa9b;\n" +
            "  ifa8->hdr;\n" +
            "  ifa9b->hel1;\n" +
            "  ifa9b->end;\n" +
            "  hel1->hel2;\n" +
            "  hel2->hdr;\n" +
            "}"
        );
        /*
        Expected output:
        
        vara;
        while(true) {
            if (hdr) {
                break;
            }
            if (!ifa9) {
                if (!ifa8) {
                    continue;
                }
                if (!ifa9b) {
                    break;
                }
                hel1;
            }
            hel2;
        }
        end;
        */
        
        // Example 22: Complex nested ifs in loop with continue and break
        runExample("Example 22: Complex nested ifs in loop with continue and break",
            "digraph pcode {\n" +
            "  start -> loc0000;\n" +
            "  loc0037 -> loc0058;\n" +
            "  loc0015 -> loc0047;\n" +
            "  loc0015 -> loc001f;\n" +
            "  loc003d -> loc0047;\n" +
            "  loc001f -> loc002e;\n" +
            "  loc001f -> loc0028;\n" +
            "  loc0000 -> loc0051;\n" +
            "  loc0047 -> loc0051;\n" +
            "  loc0028 -> loc0051;\n" +
            "  loc0051 -> loc0015;\n" +
            "  loc0051 -> loc0058;\n" +
            "  loc002e -> loc003d;\n" +
            "  loc002e -> loc0037;\n" +
            "}"
        );
        /*
        Expected output:
        
        start;
        loc0000;
        while(true) {
            if (!loc0051) {
                break;
            }
            if (!loc0015) {        
                if (!loc001f) {
                    loc0028;
                    continue;
                }
                if (!loc002e) {
                    loc0037;
                    break;
                }
                loc003d;
            }
            loc0047;
        }
        loc0058;
        */
        
        // Example 23: Loop with nested ifs and break
        runExample("Example 23: Loop with nested ifs and break",
            "digraph pcode {\n" +
            "  start -> loc0000;\n" +
            "  loc0040 -> loc0020;\n" +
            "  loc0040 -> loc0044;\n" +
            "  loc002d -> loc0040;\n" +
            "  loc002d -> loc003a;\n" +
            "  loc0020 -> loc002d;\n" +
            "  loc0000 -> loc002d;\n" +
            "  loc003a -> loc0040;\n" +
            "}"
        );
        /*
        Expected output:
        
        start;
        loc0000;
        while(true) {
            if (!loc002d) {
                loc003a;
            }
            if (!loc0040) {
                break;
            }
            loc0020;
        }
        loc0044;
        */
        
        // Example 24: Loop with labeled break to different exit
        runExample("Example 24: Loop with labeled break to different exit",
            "digraph {\n" +
            "  start;\n" +
            "  start->hdr;\n" +
            "  hdr->after;\n" +
            "  hdr->body;\n" +
            "  body->ifr;\n" +
            "  ifr->r;\n" +
            "  ifr->hdr;\n" +
            "  after->end;\n" +
            "}"
        );
        /*
        Expected output:
        
        start;
        block_0: {
            while(true) {
                if (hdr) {
                    break;
                }
                body;
                if (ifr) {
                    r;
                    break block_0;
                }
            }
            after;
            end;
        }
        */
        
        // Example 25: Complex nested ifs with converging paths
        runExample("Example 25: Complex nested ifs with converging paths",
            "digraph pcode {\n" +
            "  start -> loc0000;\n" +
            "  loc00d9 -> loc00f9;\n" +
            "  loc00d9 -> loc00ec;\n" +
            "  loc0015 -> loc0036;\n" +
            "  loc0015 -> loc0029;\n" +
            "  loc009c -> loc010a;\n" +
            "  loc005c -> loc007f;\n" +
            "  loc005c -> loc006f;\n" +
            "  loc00ac -> loc00cf;\n" +
            "  loc00ac -> loc00bf;\n" +
            "  loc006f -> loc010a;\n" +
            "  loc00f9 -> loc0103;\n" +
            "  loc0036 -> loc00d9;\n" +
            "  loc0036 -> loc0049;\n" +
            "  loc00bf -> loc010a;\n" +
            "  loc0103 -> loc0015;\n" +
            "  loc0103 -> loc010a;\n" +
            "  loc007f -> loc0089;\n" +
            "  loc0000 -> loc0103;\n" +
            "  loc0089 -> loc00ac;\n" +
            "  loc0089 -> loc009c;\n" +
            "  loc00cf -> loc00d9;\n" +
            "  loc0049 -> loc0089;\n" +
            "  loc0049 -> loc005c;\n" +
            "}"
        );
        /*
        Expected output:
        
        start;
        loc0000;
        block_0: {
            while(true) {
                if (!loc0103) {
                    break;
                }
                if (!loc0015) {
                    loc0029;
                    break block_0;
                }
                if (!loc0036) {
                    if (!loc0049) {
                        if (!loc005c) {
                            loc006f;
                            break;
                        }
                        loc007f;
                    }
                    if (!loc0089) {
                        loc009c;
                        break;
                    }
                    if (!loc00ac) {
                        loc00bf;
                        break;
                    }
                    loc00cf;
                }
                if (!loc00d9) {
                    loc00ec;
                    break block_0;
                }
                loc00f9;
            }
            loc010a;
        }
        */
        
        // Example 26: Loop with if-else branches converging to common point
        runExample("Example 26: Loop with if-else branches converging to common point",
            "digraph pcode {\n" +
            "  start -> loc0000;\n" +
            "  loc005a -> loc0064;\n" +
            "  loc007b -> loc0024;\n" +
            "  loc007b -> loc0082;\n" +
            "  loc0038 -> loc0047;\n" +
            "  loc0038 -> loc0041;\n" +
            "  loc0064 -> loc007b;\n" +
            "  loc0064 -> loc006d;\n" +
            "  loc0041 -> loc0082;\n" +
            "  loc0000 -> loc007b;\n" +
            "  loc0024 -> loc005a;\n" +
            "  loc0024 -> loc0038;\n" +
            "  loc0047 -> loc0064;\n" +
            "  loc0047 -> loc0050;\n" +
            "  loc0050 -> loc0082;\n" +
            "}"
        );
        /*
        Expected output:
        
        start;
        loc0000;
        block_0: {
            while(true) {
                if (!loc007b) {
                    break;
                }
                if (!loc0024) {
                    if (!loc0038) {
                        loc0041;
                        break;
                    }
                    if (!loc0047) {
                        loc0050;
                        break;
                    }
                } else {
                    loc005a;
                }
                if (!loc0064) {
                    loc006d;
                    break block_0;
                }
            }
            loc0082;
        }
        */
        
        // Example 27: Loop with nested conditionals and skip pattern to back-edge
        runExample("Example 27: Loop with nested conditionals and skip pattern",
            "digraph pcode {\n" +
            "  start -> loc0000;\n" +
            "  loc00c0 -> loc00c1;\n" +
            "  loc0066 -> loc00ab;\n" +
            "  loc0066 -> loc0085;\n" +
            "  loc00a5 -> loc00ab;\n" +
            "  loc0000 -> loc0051;\n" +
            "  loc00ab -> loc00b1;\n" +
            "  loc00b1 -> loc0051;\n" +
            "  loc0051 -> loc00c0;\n" +
            "  loc0051 -> loc0066;\n" +
            "  loc0085 -> loc00a5;\n" +
            "  loc0085 -> loc00b1;\n" +
            "}"
        );
        /*
        Expected output:
        
        loc0000;
        while(true) {
            if (loc0051) {
                break;
            }
            block_1: {
                if (!loc0066) {
                    if (!loc0085) {
                        break;
                    }
                    loc00a5;
                }
                loc00ab;
            }
            loc00b1;
        }
        loc00c0;
        loc00c1;
        */
        
        // Example 28: Loop with switch and labeled block break
        System.out.println();
        runExample("Example 28: Loop with switch and labeled block break",
            "digraph pcode {\n" +
            "  start -> loc0000;\n" +
            "  loc0065 -> loc00b6;\n" +
            "  loc0065 -> loc0081;\n" +
            "  loc00f1 -> loc00f7;\n" +
            "  loc00f7 -> loc00fd;\n" +
            "  loc00fd -> loc0103;\n" +
            "  loc0103 -> loc0050;\n" +
            "  loc0081 -> loc00c1;\n" +
            "  loc0081 -> loc0091;\n" +
            "  loc0091 -> loc00cc;\n" +
            "  loc0091 -> loc00a1;\n" +
            "  loc0112 -> loc0113;\n" +
            "  loc00a1 -> loc00d7;\n" +
            "  loc00a1 -> loc00f7;\n" +
            "  loc00b6 -> loc0103;\n" +
            "  loc00c1 -> loc00fd;\n" +
            "  loc0000 -> loc0050;\n" +
            "  loc00cc -> loc00fd;\n" +
            "  loc00d7 -> loc00f1;\n" +
            "  loc00d7 -> loc0103;\n" +
            "  loc0050 -> loc0112;\n" +
            "  loc0050 -> loc0065;\n" +
            "  loc0065[_operator=\"===\"];\n" +
            "  loc0081[_operator=\"===\"];\n" +
            "  loc0091[_operator=\"===\"];\n" +
            "  loc00a1[_operator=\"===\"];\n" +
            "}"
        );
        /*
        Expected output:
        
        start;
        loc0000;
        while(true) {
            if (loc0050) {
                break;
            }
            block_1: {
                if (!loc0065) {
                    if (!loc0081) {
                        if (!loc0091) {
                            if (loc00a1) {
                                if (!loc00d7) {
                                    break;
                                }
                                loc00f1; 
                            }
                            loc00f7;
                        } else {
                            loc00cc;
                        }
                    } else {
                        loc00c1;
                    }
                    loc00fd;
                } else {
                    loc00b6;
                }
            }
            loc0103;            
        }
        loc0112;
        loc0113;
        
        */
        /*
        Code with switches:
        
        start;
        loc0000;
        while(true) {
            if (loc0050) {
                break;
            }
            block_1: {
                switch {
                    case loc0065:
                        loc00b6;
                        break block_1;
                    case loc0081:
                        loc00c1;
                        break;
                    case loc0091:
                        loc00cc;
                        break; 
                    case loc00a1:
                        if (!loc00d7) {
                            break block_1;
                        }
                        loc00f1;                
                    default:
                        loc00f7;
                }
                loc00fd;
            }
            loc0103;
        }
        loc0112;
        loc0113;
        */
        
        // Example 29: Loop with if-else header and break after merge
        runExample("Example 29: Loop with if-else header and break after merge",
            "digraph pcode {\n" +
            "start -> loc0000;\n" +
            "loc0067 -> loc0025;\n" +
            "loc0067 -> loc007b;\n" +
            "loc007b -> loc007c;\n" +
            "loc0044 -> loc0067;\n" +
            "loc0000 -> loc0025;\n" +
            "loc0058 -> loc0067;\n" +
            "loc0025 -> loc0058;\n" +
            "loc0025 -> loc0044;\n" +
            "}"
        );
        /*
        Expected output:
        
        start;
        loc0000;
        while(true) {
            if (!loc0025) {
                loc0044;
            } else {
                loc0058;
            }
            if (!loc0067) {
                break;
            }
        }
        loc007b;
        loc007c;
        */
        
        // Example 30: Loop with if-then merged to condition with break
        runExample("Example 30: Loop with if-then merged to condition with break",
            "digraph pcode {\n" +
            "start -> loc0000;\n" +
            "loc0032 -> loc0058;\n" +
            "loc0032 -> loc0048;\n" +
            "loc0077 -> loc008e;\n" +
            "loc0048 -> loc0058;\n" +
            "loc0000 -> loc0032;\n" +
            "loc0058 -> loc0077;\n" +
            "loc0058 -> loc005e;\n" +
            "loc005e -> loc0032;\n" +
            "}"
        );
        /*
        Expected output:
        
        start;
        loc0000;
        while(true) {
            if (!loc0032) {
                loc0048;
            }
            if (loc0058) {
                break;
            }
            loc005e;
        }
        loc0077;
        loc008e;
        */
        
        // Example 31: Loop header with labeled block and skip pattern
        runExample("Example 31: Loop header with labeled block and skip pattern",
            "digraph pcode {\n" +
            "start -> loc0000;\n" +
            "loc0078 -> loc007e;\n" +
            "loc007e -> loc0037;\n" +
            "loc007e -> loc00a3;\n" +
            "loc004c -> loc0072;\n" +
            "loc004c -> loc0067;\n" +
            "loc0000 -> loc0037;\n" +
            "loc0067 -> loc007e;\n" +
            "loc0037 -> loc0078;\n" +
            "loc0037 -> loc004c;\n" +
            "loc00a3 -> loc00aa;\n" +
            "loc0072 -> loc0078;\n" +
            "}"
        );
        /*
        Expected output:
        
        start;
        loc0000;
        while(true) {
            block_1: {
                if(!loc0037) {
                    if (!loc004c) {
                        loc0067;
                        break;
                    }
                    loc0072;
                }
                loc0078;
            }
            if (!loc007e) {
                break;
            }
        }
        loc00a3;
        loc00aa;
        */
        
        // Example 32: Loop header with labeled block and skip pattern (variant)
        runExample("Example 32: Loop header with labeled block and skip pattern (variant)",
            "digraph pcode {\n" +
            "start -> loc0000;\n" +
            "loc0075 -> loc0081;\n" +
            "loc0081 -> loc008b;\n" +
            "loc00bb -> loc00c2;\n" +
            "loc0055 -> loc0075;\n" +
            "loc0055 -> loc008b;\n" +
            "loc008b -> loc0039;\n" +
            "loc008b -> loc00bb;\n" +
            "loc0000 -> loc0039;\n" +
            "loc0039 -> loc0081;\n" +
            "loc0039 -> loc0055;\n" +
            "}"
        );
        /*
        Expected output:
        
        start;
        loc0000;
        while(true) {
            block_1: {
                if (!loc0039) {
                    if (!loc0055) {
                        break;
                    }
                    loc0075;
                }
                loc0081;
            }
            if (!loc008b) {
                break;
            }
        }
        loc00bb;
        loc00c2;
        */
        
        // Example 33: Switch with default in middle (case and default share body)
        runExample("Example 33: Switch with default in middle position",
            "digraph pcode {\n" +
            "start -> loc0000;\n" +
            "loc009c -> loc00a2;\n" +
            "loc00a2 -> loc00a9;\n" +
            "loc0066 -> loc0091;\n" +
            "loc0066 -> loc0076;\n" +
            "loc0076 -> loc009c;\n" +
            "loc0076 -> loc008b;\n" +
            "loc0000 -> loc008b;\n" +
            "loc0000 -> loc0066;\n" +
            "loc008b -> loc0091;\n" +
            "loc0091 -> loc00a2;\n" +
            "loc0000[_operator=\"===\"];\n" +
            "loc0066[_operator=\"===\"];\n" +
            "loc0076[_operator=\"===\"];\n" +
            "}"
        );
        /*
        Expected output:
        
        start;
        switch {
            case loc0000:
            default:
                loc008b;
            case loc0066:
                loc0091;
                break;
            case loc0076:
                loc009c;
        }
        loc00a2;
        loc00a9;
        */
        
        // Example 34: Switch with default before case (default falls through to case body)
        runExample("Example 34: Switch with default falling through to case body",
            "digraph pcode {\n" +
            "start -> loc0000;\n" +
            "loc009c -> loc00ad;\n" +
            "loc00a7 -> loc00ad;\n" +
            "loc0066 -> loc009c;\n" +
            "loc0066 -> loc0076;\n" +
            "loc00ad -> loc00b4;\n" +
            "loc0076 -> loc00a7;\n" +
            "loc0076 -> loc0096;\n" +
            "loc0000 -> loc008b;\n" +
            "loc0000 -> loc0066;\n" +
            "loc008b -> loc00ad;\n" +
            "loc0096 -> loc009c;\n" +
            "loc0000[_operator=\"===\"];\n" +
            "loc0066[_operator=\"===\"];\n" +
            "loc0076[_operator=\"===\"];\n" +
            "}"
        );
        /*
        Expected output:
        
        start;
        switch {
            case loc0000:
                loc008b;
                break;
            default:
                loc0096;
            case loc0066:
                loc009c;
                break;
            case loc0076:
                loc00a7;
        }
        loc00ad;
        loc00b4;
        */
    }
}
