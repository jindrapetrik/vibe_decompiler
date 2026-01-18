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
            "  if_cond->then;\n" +
            "  if_cond->else;\n" +
            "  then->merge;\n" +
            "  else->merge;\n" +
            "  merge->exit;\n" +
            "}"
        );
        
        // Example 2: While loop
        System.out.println();
        runExample("Example 2: While Loop",
            "digraph {\n" +
            "  entry->loop_header;\n" +
            "  loop_header->loop_body;\n" +
            "  loop_header->exit;\n" +
            "  loop_body->loop_header;\n" +
            "}"
        );
        
        // Example 3: Loop with break and continue
        System.out.println();
        runExample("Example 3: Loop with Break and Continue",
            "digraph {\n" +
            "  entry->loop_header;\n" +
            "  loop_header->body_1;\n" +
            "  loop_header->exit;\n" +
            "  body_1->cond_break;\n" +
            "  cond_break->body_2;\n" +
            "  cond_break->exit;\n" +
            "  body_2->cond_continue;\n" +
            "  cond_continue->body_3;\n" +
            "  cond_continue->loop_header;\n" +
            "  body_3->loop_header;\n" +
            "}"
        );
        
        // Example 4: Nested loops
        System.out.println();
        runExample("Example 4: Nested Loops",
            "digraph {\n" +
            "  entry->outer_header;\n" +
            "  outer_header->inner_header;\n" +
            "  outer_header->exit;\n" +
            "  inner_header->inner_body;\n" +
            "  inner_header->outer_end;\n" +
            "  inner_body->inner_header;\n" +
            "  outer_end->outer_header;\n" +
            "}"
        );
        
        // Example 5: If inside loop
        System.out.println();
        runExample("Example 5: If Inside Loop",
            "digraph {\n" +
            "  entry->loop_header;\n" +
            "  loop_header->if_cond;\n" +
            "  loop_header->exit;\n" +
            "  if_cond->if_then;\n" +
            "  if_cond->if_else;\n" +
            "  if_then->loop_end;\n" +
            "  if_else->loop_end;\n" +
            "  loop_end->loop_header;\n" +
            "}"
        );
        
        // Example 6: Chained edges (demonstrating a->b->c syntax)
        System.out.println();
        runExample("Example 6: Chained Edges",
            "digraph {\n" +
            "  start->if1;\n" +
            "  if1->ontrue;\n" +
            "  if1->onfalse;\n" +
            "  ontrue->merge;\n" +
            "  onfalse->merge;\n" +
            "  merge->after->exit;\n" +
            "}"
        );
        
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
            "  ternar->pre_inc_a;\n" +
            "  ternar->pre_inc_b;\n" +
            "  pre_inc_a->inc_c;\n" +
            "  pre_inc_b->inc_c;\n" +
            "  inc_c->loop_a_cond;\n" +
            "}"
        );
        
        // Example 8: Do-while style loop (body first, then condition)
        System.out.println();
        runExample("Example 8: Do-While Style Loop",
            "digraph {\n" +
            "  entry->body;\n" +
            "  body->cond;\n" +
            "  cond->body;\n" +
            "  cond->exit;\n" +
            "}"
        );

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

        // Example 10: Two nested while loops with labeled blocks inside
        System.out.println();
        runExample("Example 10: Nested Ifs with While Loop",
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
            "  A2->start2;\n" +
            "  start2->ifex2;\n" +
            "  ifex2->end;\n" +
            "  ifex2->ifa2;\n" +
            "  ifa2->x2;\n" +
            "  ifa2->A12;\n" +
            "  x2->ifc2;\n" +
            "  ifc2->y2;\n" +
            "  ifc2->z2;\n" +
            "  y2->A22;\n" +
            "  z2->A12;\n" +
            "  A12->d2;\n" +
            "  d2->A22;\n" +
            "  A22->start3;\n" +
            "  start3->ifex3;\n" +
            "  ifex3->end;\n" +
            "  ifex3->ifa3;\n" +
            "  ifa3->x3;\n" +
            "  ifa3->A13;\n" +
            "  x3->ifc3;\n" +
            "  ifc3->y3;\n" +
            "  ifc3->z3;\n" +
            "  y3->A23;\n" +
            "  z3->A13;\n" +
            "  A13->d3;\n" +
            "  d3->A23;\n" +
            "  A23->start2;\n" +
            "}",
            true
        );

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
            "}",
            true
        );

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
            "}",
            true
        );

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
            "}",
            true
        );

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
            "}",
            true
        );
    }
}
