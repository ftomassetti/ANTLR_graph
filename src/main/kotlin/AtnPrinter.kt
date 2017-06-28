package me.tomassetti.antlrgraph

import org.antlr.v4.runtime.Parser
import org.antlr.v4.runtime.Vocabulary
import org.antlr.v4.runtime.atn.ATN
import org.antlr.v4.runtime.atn.ATNState
import org.antlr.v4.runtime.atn.Transition
import org.snt.inmemantlr.GenericParser
import org.snt.inmemantlr.memobjects.MemoryTupleSet
import java.awt.Color
import java.io.File
import java.io.PrintWriter


fun ATNState.name() = "state${this.stateNumber}"
fun ATNState.label(ruleNames: Array<String>) = if (this.ruleIndex == -1) name() else "[$stateNumber] ${this.javaClass.simpleName.removeSuffix("State")} ${ruleNames[this.ruleIndex]}"

//val rulesToExclude = setOf(MiniCalcParser.RULE_expression)

fun colorForRule(index: Int) : Color {
    val r = (index * 7) % 128 + 128
    val g = (index * 31) % 128 + 128
    val b = (index * 53) % 128 + 128
    return Color(r, g, b)
}

fun Color.darkVersion() : Color {
    val r = (this.red/2)
    val g = (this.green/2)
    val b = (this.blue/2)
    return Color(r, g, b)
}

fun Color.lightVersion() : Color {
    val r = 255 - ((255 - this.red)/4)
    val g = 255 - ((255 - this.green)/4)
    val b = 255 - ((255 - this.blue)/4)
    return Color(r, g, b)
}

fun Int.toHex() : String {
    val s = Integer.toHexString(this)
    return if (s.length == 1) "0$s" else s
}

fun Color.toHtml() : String {
    return "#${red.toHex()}${green.toHex()}${blue.toHex()}"
}

private fun ATNState.isConnectedToRule(ruleIndex: Int): Boolean {
    return this.ruleIndex == ruleIndex || this.transitions.any { it.isConnectedToRule(ruleIndex) }
    || this.atn.states.filter { it.ruleIndex == ruleIndex }.any { it.isConnectedToState(this) }
}

private fun ATNState.isConnectedToState(atnState: ATNState): Boolean {
    return this.transitions.any { it.target == atnState }
}

private fun Transition.isConnectedToRule(ruleIndex: Int): Boolean = this.target.ruleIndex == ruleIndex

private fun writeCluster(atn: ATN, vocabulary: Vocabulary, ruleNames: Array<String>, ruleIndex: Int, out: PrintWriter, onlyRelationTo: Int? = null) {
    out.println("subgraph cluster_$ruleIndex {")
    if (onlyRelationTo != null && onlyRelationTo != ruleIndex) {
        out.println("  style = dotted;")
    }
    atn.states.filter { !it.isOrphan() }.filter { it.ruleIndex == ruleIndex }.forEach { state ->
        if (onlyRelationTo == null || state.isConnectedToRule(onlyRelationTo)) {
            out.println("    ${state.name()} [shape=rectangle, label=\"${state.label(ruleNames)}\", fillcolor=\"${colorForRule(state.ruleIndex).toHtml()}\", style=\"filled\"];")
        }
    }
    atn.states.filter { it.ruleIndex == ruleIndex }.forEach { state ->
        state.transitions.filter { it.target.ruleIndex == ruleIndex }.forEach { transition ->
            if (onlyRelationTo == null || transition.isConnectedToRule(onlyRelationTo)) {
                if (transition.isEpsilon) {
                    out.println("    ${state.name()} -> ${transition.target.name()} [color=\"${colorForRule(state.ruleIndex).darkVersion().toHtml()}\"]")
                } else {
                    transition.label().toList().forEach { item ->
                        out.println("    ${state.name()} -> ${transition.target.name()} [label=\"${vocabulary.getDisplayName(item)}\", color=\"${colorForRule(state.ruleIndex).darkVersion().toHtml()}\"]")
                    }
                }
            }
        }
    }

    out.println("    label = \"${ruleNames[ruleIndex]}\";")
    out.println("    bgcolor = \"${colorForRule(ruleIndex).lightVersion().toHtml()}\";")
    out.println("}")
}

private fun ATNState.isOrphan(): Boolean {
    return this.transitions.isEmpty() && !this.atn.states.any { it.isConnectedToState(this) }
}

class MyClassLoader(val bytecodeInMemory: MemoryTupleSet) : ClassLoader() {
    override fun findClass(name: String?): Class<*>? {
        val element = bytecodeInMemory.map { it.byteCodeObjects.find { it.className == name } }.find { it != null}
        return if (element == null) {
            null
        } else {
            defineClass(name, element.bytes, 0, element.bytes.size)
        }
    }
}

fun main(args: Array<String>) {
    //args.forEach {
    val files : Array<File> = args.map { File(it) }.toTypedArray()
    val gp = GenericParser(*files)
    gp.compile()

    val set = gp.allCompiledObjects
    // memory tuple contains the generated source code of ANTLR
    // and the associated byte code
    val myClassLoader = MyClassLoader(set)
    val myClasses = HashMap<String, Class<*>>()
    for (tup in set) {
        for (byteCodeObject in tup.byteCodeObjects) {
            val c = myClassLoader.loadClass(byteCodeObject.className)
            myClasses[c.canonicalName] = c
        }
    }
    val parserClass = myClasses.values.find { it.superclass?.canonicalName == Parser::class.java.canonicalName }!!
    val atn = parserClass.getField("_ATN").get(null) as ATN
    val ruleNames = parserClass.getField("ruleNames").get(null) as Array<String>
    val vocabulary = parserClass.getField("VOCABULARY").get(null) as Vocabulary

    val nRules = atn.ruleToStartState.size

    for (ruleIndex in 0..(nRules-1)) {
        val ruleName = ruleNames[ruleIndex]
        drawClusters("clusters_for_$ruleName.dot", "ATN for rule $ruleName", atn, vocabulary, ruleNames, nRules, ruleIndex)
    }


    //for (ruleIndex in 0..(nRules-1)) {
        File("atn.dot").printWriter().use { out ->
            out.println("digraph ATN {")
            atn.states.forEach { state ->
                out.println("    ${state.name()} [shape=rectangle, label=\"${state.label(ruleNames)}\", fillcolor=\"${colorForRule(state.ruleIndex).toHtml()}\", style=filled];")
            }
            atn.states.forEach { state ->
                state.transitions.forEach { transition ->
                    if (transition.isEpsilon) {
                        out.println("    ${state.name()} -> ${transition.target.name()}")
                    } else {
                        transition.label().toList().forEach { item ->
                            out.println("    ${state.name()} -> ${transition.target.name()} [label=\"${vocabulary.getDisplayName(item)}\"]")
                        }
                    }
                }
            }
            out.println("    labelloc=\"t\";")
            out.println("    label=\"ATN for the language\";")
            out.println("}")
        }
    //}

    for (ruleIndex in 0..(nRules-1)) {
        File("atn_${ruleIndex}.dot").printWriter().use { out ->
            out.println("digraph ATN_for_${ruleNames[ruleIndex]} {")
            atn.states.filter { it.ruleIndex == ruleIndex }.forEach { state ->
                out.println("    ${state.name()} [shape=rectangle, label=\"${state.label(ruleNames)}\"];")
            }
            atn.states.filter { it.ruleIndex == ruleIndex }.forEach { state ->
                state.transitions/*.filter { !rulesToExclude.contains(it.target.ruleIndex) }*/.forEach { transition ->
                    if (transition.isEpsilon) {
                        out.println("    ${state.name()} -> ${transition.target.name()}")
                    } else {
                        transition.label().toList().forEach { item ->
                            out.println("    ${state.name()} -> ${transition.target.name()} [label=\"${vocabulary.getDisplayName(item)}\"]")
                        }
                    }
                }
            }
            out.println("    labelloc=\"t\";")
            out.println("    label=\"ATN for ${ruleNames[ruleIndex]}\";")
            out.println("}")
        }
    }

}

private fun drawClusters(fileName: String, title: String, atn: ATN, vocabulary: Vocabulary, ruleNames: Array<String>, nRules: Int, onlyRelationTo: Int? = null) {
    File(fileName).printWriter().use { out ->
        out.println("digraph ATN {")
        out.println("   remincross =true")
        for (ruleIndex in 0..(nRules - 1)) {
            writeCluster(atn, vocabulary, ruleNames, ruleIndex, out, onlyRelationTo)
        }

        //atn.states.forEach { state ->
        //    out.println("    ${state.name()} [shape=rectangle, label=\"${state.label()}\", fillcolor=\"${colorForRule(state.ruleIndex).toHtml()}\", style=filled];")
        //}
        interClusterRelations(atn, vocabulary, out, onlyRelationTo)
        out.println("    labelloc=\"t\";")
        out.println("    label=\"$title\";")
        out.println("}")
    }
}

private fun interClusterRelations(atn: ATN, vocabulary: Vocabulary, out: PrintWriter, onlyRelationTo: Int? = null) {
    atn.states.forEach { state ->
        state.transitions.filter { state.ruleIndex != it.target.ruleIndex }.forEach { transition ->
            if (onlyRelationTo == null || transition.isConnectedToRule(onlyRelationTo) || state.ruleIndex == onlyRelationTo) {
                if (transition.isEpsilon) {
                    out.println("    ${state.name()} -> ${transition.target.name()} [color=\"${colorForRule(transition.target.ruleIndex).darkVersion().toHtml()}\"]")
                } else {
                    transition.label().toList().forEach { item ->
                        out.println("    ${state.name()} -> ${transition.target.name()} [label=\"${vocabulary.getDisplayName(item)}\", color=\"${colorForRule(transition.target.ruleIndex).darkVersion().toHtml()}\"]")
                    }
                }
            }
        }
    }
}