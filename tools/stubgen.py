#!/usr/bin/env python3
"""Generate compile-only Java stubs for Minecraft from the javap dumps in tools/probe/<ver>.txt.

The stubs carry the exact member signatures of every probed class, so era code can be
compile-checked against any of the 33 versions without Mojang/Maven access:

    python3 tools/stubgen.py 1.21.1            # -> /tmp/stubs/1.21.1/{src,classes}
    python3 tools/stubgen.py --all

Referenced but unprobed types become empty classes or interfaces with the right generic arity.
Member bodies throw, so stubs are only for compiling, never running. Bodies of abstract
methods are kept abstract only for single-method interfaces (to keep lambdas legal).
"""
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROBE = os.path.join(ROOT, "tools", "probe")
OUT = os.environ.get("AETHERIUM_STUBS", "/tmp/stubs")
TC = os.environ.get("AETHERIUM_TC", "/tmp/tc")

MODS = {"public", "protected", "private", "static", "final", "abstract", "native",
        "synchronized", "transient", "volatile", "strictfp", "default", "sealed", "non-sealed"}
PRIMS = {"boolean", "byte", "char", "short", "int", "long", "float", "double", "void"}
TYPE_RE = re.compile(r"\b((?:[a-z_][\w]*\.)+[A-Z_][\w$]*)")
JDK_PREFIXES = ("java.", "javax.", "jdk.", "sun.")
# Hand-written in tools/stubs_lib; never generate (an empty generated class would shadow them).
LIB_PREFIXES = ("org.slf4j.", "org.apache.logging.", "org.objectweb.", "org.spongepowered.",
                "net.fabricmc.", "net.neoforged.", "org.lwjgl.")
# Exact library types hand-written in tools/stubs_lib (Guava pieces the mod implements or calls).
LIB_TYPES = {"com.google.common.collect.Table", "com.google.common.collect.Table$Cell",
             "com.google.common.collect.ImmutableTable", "com.google.common.collect.ImmutableTable$Builder",
             "com.google.common.collect.ImmutableMap", "com.google.common.collect.ImmutableSet",
             "com.google.common.collect.ImmutableCollection", "com.google.common.collect.Multimap"}

# Unprobed types the mod relies on: superclass + members, from stable Mojang API (not from
# the probe). The real CI build is the authority for these; keep the list short.
HINTS = {
    "net.minecraft.client.player.LocalPlayer": ("net.minecraft.client.player.AbstractClientPlayer", []),
    "net.minecraft.client.player.AbstractClientPlayer": ("net.minecraft.world.entity.player.Player", []),
    "net.minecraft.world.entity.player.Player": ("net.minecraft.world.entity.LivingEntity", []),
    "net.minecraft.world.entity.monster.Monster": ("net.minecraft.world.entity.LivingEntity", []),
    "net.minecraft.world.level.block.state.BlockState": ("net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase", []),
    "net.minecraft.world.level.block.Block": (None, ["public net.minecraft.world.level.block.state.BlockState defaultBlockState();"]),
    "net.minecraft.core.Vec3i": (None, ["public int getX();", "public int getY();", "public int getZ();"]),
    "net.minecraft.core.Holder$Reference": (None, ["public T0 value();"]),
    "net.minecraft.client.resources.sounds.AbstractSoundInstance": (None, []),
    "net.minecraft.world.level.block.entity.BeaconBlockEntity": ("net.minecraft.world.level.block.entity.BlockEntity", []),
    "net.minecraft.world.level.block.entity.TheEndPortalBlockEntity": ("net.minecraft.world.level.block.entity.BlockEntity", []),
    "net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity": ("net.minecraft.world.level.block.entity.TheEndPortalBlockEntity", []),
    "it.unimi.dsi.fastutil.objects.ObjectArrayList": (None, ["public T0 get(int);", "public int size();"]),
    # 1.21+: a record (format, vertexCount, indexCount, mode, indexType) per its probed constructor;
    # callers catch LinkageError in case an accessor name differs.
    "com.mojang.blaze3d.vertex.MeshData$DrawState": (None, [
        "public com.mojang.blaze3d.vertex.VertexFormat format();",
        "public com.mojang.blaze3d.vertex.VertexFormat$Mode mode();"]),
    # joml is not on the probe classpath (a library jar); public API since joml 1.9.
    "org.joml.Quaternionf": (None, ["public org.joml.Quaternionf();", "public float x;", "public float y;", "public float z;", "public float w;",
                                    "public org.joml.Quaternionf rotationX(float);", "public org.joml.Quaternionf rotationY(float);",
                                    "public org.joml.Quaternionf rotationZ(float);"]),
    "org.joml.Matrix4f": (None, [
        "public org.joml.Matrix4f();",
        "public org.joml.Matrix4f set(float[]);",
        "public float[] get(float[]);"]),
    "net.minecraft.world.level.LevelAccessor": (None, [
        "public float getTimeOfDay(float);",
        "public int getMoonPhase();"]),
    "net.minecraft.Util$OS": (None, [
        "public void openFile(java.io.File);",
        "public void openPath(java.nio.file.Path);"]),
    "net.minecraft.client.gui.components.Button$Builder": (None, [
        "public net.minecraft.client.gui.components.Button$Builder bounds(int, int, int, int);",
        "public net.minecraft.client.gui.components.Button build();"]),
    "org.joml.Matrix3f": (None, ["public float m00();", "public float m01();", "public float m02();",
                                 "public float m10();", "public float m11();", "public float m12();",
                                 "public float m20();", "public float m21();", "public float m22();"]),
    "org.joml.Vector3f": (None, ["public float x();", "public float y();", "public float z();"]),
    "it.unimi.dsi.fastutil.objects.ObjectList": (None, ["public T0 get(int);", "public int size();"]),
    "net.minecraft.world.phys.AABB": (None, ["public final double minX;", "public final double minY;",
                                             "public final double minZ;", "public final double maxX;",
                                             "public final double maxY;", "public final double maxZ;"]),
    # 1.17.2 (1.16.5 performance mixins): hierarchy of unprobed types.
    "net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity": ("net.minecraft.world.level.block.entity.BlockEntity", []),
    "net.minecraft.world.level.block.AbstractChestBlock": ("net.minecraft.world.level.block.Block", []),
    "net.minecraft.world.level.block.state.properties.DirectionProperty": ("net.minecraft.world.level.block.state.properties.Property", []),
    "net.minecraft.world.level.block.state.properties.EnumProperty": ("net.minecraft.world.level.block.state.properties.Property", []),
    "net.minecraft.world.level.chunk.LevelChunk$EntityCreationType": (None, [
        "public static final net.minecraft.world.level.chunk.LevelChunk$EntityCreationType CHECK;",
        "public static final net.minecraft.world.level.chunk.LevelChunk$EntityCreationType IMMEDIATE;"]),
    "net.minecraft.world.entity.LightningBolt": ("net.minecraft.world.entity.Entity", []),
    "net.minecraft.world.entity.boss.wither.WitherBoss": ("net.minecraft.world.entity.monster.Monster", []),
    "net.minecraft.world.entity.item.FallingBlockEntity": ("net.minecraft.world.entity.Entity", []),
    "net.minecraft.world.entity.item.PrimedTnt": ("net.minecraft.world.entity.Entity", []),
    "net.minecraft.world.entity.projectile.EyeOfEnder": ("net.minecraft.world.entity.Entity", []),
    "net.minecraft.util.profiling.ProfilerFiller": (None, ["public void push(java.lang.String);", "public void pop();"]),
    "net.minecraft.world.item.Items": (None, ["public static final net.minecraft.world.item.Item LAVA_BUCKET;",
                                             "public static final net.minecraft.world.item.Item TORCH;",
                                             "public static final net.minecraft.world.item.Item GLOWSTONE;"]),
}

# Unprobed types that need type parameters or interfaces in their stub.
HINT_ARITY = {
    "net.minecraft.core.Holder$Reference": 1,
    "it.unimi.dsi.fastutil.objects.ObjectArrayList": 1,
    "it.unimi.dsi.fastutil.objects.ObjectList": 1,
}
# Unprobed library types that are interfaces (stubbed as classes otherwise).
HINT_INTERFACES = {"org.joml.Quaternionfc", "org.joml.Matrix4fc", "net.minecraft.world.level.LevelAccessor"}
HINT_IMPLEMENTS = {
    "net.minecraft.client.resources.sounds.AbstractSoundInstance": ["net.minecraft.client.resources.sounds.SoundInstance"],
    "org.joml.Quaternionf": ["org.joml.Quaternionfc"],
    "org.joml.Matrix4f": ["org.joml.Matrix4fc"],
    "net.minecraft.world.level.LevelAccessor": ["net.minecraft.world.level.BlockAndTintGetter"],
}


def split_top(s, sep=","):
    out, depth, cur = [], 0, []
    for ch in s:
        if ch == "<":
            depth += 1
        elif ch == ">":
            depth -= 1
        if ch == sep and depth == 0:
            out.append("".join(cur).strip())
            cur = []
        else:
            cur.append(ch)
    if "".join(cur).strip():
        out.append("".join(cur).strip())
    return out


def tokens_top(s):
    """Space-separated tokens, keeping <...> groups (which may contain spaces) intact."""
    out, depth, cur = [], 0, []
    for ch in s:
        if ch == "<":
            depth += 1
        elif ch == ">":
            depth -= 1
        if ch == " " and depth == 0:
            if cur:
                out.append("".join(cur))
            cur = []
        else:
            cur.append(ch)
    if cur:
        out.append("".join(cur))
    return out


def jtype(t):
    """javap type -> Java source type (nested '$' -> '.')."""
    return re.sub(r"(?<=[\w])\$(?=[A-Za-z_])", ".", t)


def is_jdk(name):
    return name.startswith(JDK_PREFIXES) or name.startswith(LIB_PREFIXES) or name in LIB_TYPES


def default_value(t):
    t = t.strip()
    if t == "boolean":
        return "false"
    if t in ("byte", "short", "char", "int", "long", "float", "double"):
        return "(%s) 0" % t
    return "null"


class Member:
    def __init__(self, kind, mods, typeparams, rtype, name, params, throws):
        self.kind, self.mods, self.typeparams = kind, mods, typeparams
        self.rtype, self.name, self.params, self.throws = rtype, name, params, throws


class Cls:
    def __init__(self, fq, kind, mods, typeparams, extends, implements):
        self.fq, self.kind, self.mods, self.typeparams = fq, kind, mods, typeparams
        self.extends, self.implements = extends, implements
        self.members = []
        self.enum_constants = []
        self.probed = True

    @property
    def simple(self):
        return self.fq.rsplit(".", 1)[-1].split("$")[-1]


def parse_header(line):
    line = line.rstrip(" {").strip()
    toks = tokens_top(line)
    mods = []
    i = 0
    while toks[i] in MODS:
        mods.append(toks[i])
        i += 1
    kind = toks[i]
    i += 1
    rest = " ".join(toks[i:])
    m = re.match(r"([\w.$]+)(<.*?>)?(?:\s|$)", rest)
    # type params may nest; find the matching '>'
    name = re.match(r"[\w.$]+", rest).group(0)
    after = rest[len(name):]
    typeparams = ""
    if after.startswith("<"):
        depth = 0
        for j, ch in enumerate(after):
            depth += ch == "<"
            depth -= ch == ">"
            if depth == 0:
                typeparams = after[: j + 1]
                after = after[j + 1:]
                break
    extends, implements = [], []
    after = after.strip()
    mode = None
    for tok in tokens_top(after):
        if tok in ("extends", "implements", "permits"):
            mode = tok
            continue
        for part in split_top(tok):
            if not part:
                continue
            if mode == "extends":
                extends.append(part)
            elif mode == "implements":
                implements.append(part)
    if kind == "interface":
        implements, extends = extends, []
    return Cls(name, kind, mods, typeparams, extends, implements)


def parse_member(line, cls):
    line = line.strip().rstrip(";")
    if not line or line.startswith("static {}"):
        return None
    head_name = line.split("(")[0].split(" ")[-1]
    if "$" in head_name and head_name != cls.fq:  # synthetic members; a nested class's constructor is kept
        return None
    throws = []
    if " throws " in line:
        line, th = line.split(" throws ", 1)
        throws = [t.strip() for t in th.split(",")]
    if "(" in line:
        head, params = line.split("(", 1)
        params = params.rsplit(")", 1)[0]
        params = split_top(params) if params.strip() else []
        toks = tokens_top(head.strip())
        mods, typeparams = [], ""
        while toks and (toks[0] in MODS or toks[0].startswith("<")):
            t = toks.pop(0)
            if t.startswith("<"):
                typeparams = t
            else:
                mods.append(t)
        name = toks[-1]
        rtype = " ".join(toks[:-1]) if len(toks) > 1 else None
        if rtype is None and name == cls.fq:
            return Member("ctor", mods, typeparams, None, cls.simple, params, throws)
        if rtype is None:
            return None
        return Member("method", mods, typeparams, rtype, name, params, throws)
    toks = tokens_top(line)
    mods = [t for t in toks[:-2] if t in MODS]
    if len(toks) < 2:
        return None
    name, ftype = toks[-1], toks[-2]
    if "$" in name:
        return None
    return Member("field", mods, "", ftype, name, [], [])


def parse_dump(path):
    classes = {}
    cur_section = None
    cur = None
    for raw in open(path, encoding="utf-8", errors="replace"):
        line = raw.rstrip("\n")
        if line.startswith("=== "):
            cur_section = line[4:]
            cur = None
            continue
        if cur_section is None or cur_section.startswith(("package listing", "grep ")):
            continue
        if cur is None:
            if re.match(r"^(public |protected |private |final |abstract |sealed |non-sealed |static )*(class|interface) ", line):
                cur = parse_header(line)
                if cur.fq in classes:
                    cur = None
                    continue
                classes[cur.fq] = cur
                ext = cur.extends[0] if cur.extends else ""
                if ext.startswith("java.lang.Enum<"):
                    cur.kind = "enum"
                    cur.extends = []
                elif ext == "java.lang.Record":
                    cur.extends = []
            continue
        if line.strip() == "}":
            cur = None
            continue
        m = parse_member(line, cur)
        if m is None or "private" in m.mods or re.search(r"\$\d", line) or re.match(r"(method|field|lambda)_\d+$", m.name or ""):
            continue
        if cur.kind == "enum":
            if m.kind == "field" and "static" in m.mods and jtype(m.rtype) == jtype(cur.fq) and m.name.isupper():
                cur.enum_constants.append(m.name)
                continue
            if m.kind == "ctor" or m.name in ("values", "valueOf"):
                continue
        cur.members.append(m)
    return classes


def parse_code_fields(path):
    """Public field declarations found in "=== code <class> :: <regex>" sections.

    Those sections are bytecode greps, not full dumps, but each hit is prefixed with the exact
    javap declaration of its member. For classes that are too large to dump whole (SoundEvents has
    ~1500 fields), this gives the stub the handful of fields the mod uses, with the right type for
    each version (SoundEvent vs Holder$Reference<SoundEvent>)."""
    out = {}
    cls = None
    for raw in open(path, encoding="utf-8", errors="replace"):
        line = raw.rstrip("\n")
        if line.startswith("=== "):
            m = re.match(r"=== code (\S+) ::", line)
            cls = m.group(1) if m else None
            continue
        if cls is None or " :: " not in line:
            continue
        decl = line.split(" :: ", 1)[0].strip()
        if "(" in decl or not decl.endswith(";") or not decl.startswith("public "):
            continue
        fields = out.setdefault(cls, [])
        if decl not in fields:
            fields.append(decl)
    return out


def erase(t, tvars):
    t = re.sub(r"<.*>", "", t).strip()
    if t in tvars:
        return "java.lang.Object"
    return t


def dedupe(cls):
    """Drop compiler bridges: same name/arity as an earlier method, wider or identical params."""
    tv = set(re.findall(r"(?:<|,\s*)([A-Z]\w*)", cls.typeparams))
    kept = []
    for m in cls.members:
        if m.kind != "method":
            kept.append(m)
            continue
        mtv = tv | set(re.findall(r"(?:<|,\s*)([A-Z]\w*)", m.typeparams))
        clash = False
        for k in kept:
            if k.kind != "method" or k.name != m.name or len(k.params) != len(m.params):
                continue
            ktv = tv | set(re.findall(r"(?:<|,\s*)([A-Z]\w*)", k.typeparams))
            same = True
            for a, b in zip(k.params, m.params):
                ea, eb = erase(a, ktv), erase(b, mtv)
                if not (ea == eb or eb == "java.lang.Object" or ea == "java.lang.Object"):
                    same = False
                    break
            if same:
                clash = True
                break
        if not clash:
            kept.append(m)
    cls.members = kept


def collect_refs(classes):
    refs = {}  # fq -> arity
    iface = set()

    def scan(s):
        if not s:
            return
        for m in TYPE_RE.finditer(s):
            name = m.group(1)
            if is_jdk(name):
                continue
            end = m.end()
            arity = 0
            if end < len(s) and s[end] == "<":
                depth, j, start = 0, end, end
                while j < len(s):
                    depth += s[j] == "<"
                    depth -= s[j] == ">"
                    if depth == 0:
                        break
                    j += 1
                arity = len(split_top(s[start + 1:j]))
            refs[name] = max(refs.get(name, 0), arity)

    for c in classes.values():
        scan(c.typeparams)
        for e in c.extends:
            scan(e)
        for i in c.implements:
            scan(i)
            base = re.sub(r"<.*", "", i)
            if not is_jdk(base):
                iface.add(base)
        for m in c.members:
            scan(m.typeparams)
            scan(m.rtype)
            for p in m.params:
                scan(p)
            for t in m.throws:
                scan(t)
    return refs, iface


def body_for(m):
    return "{ throw new RuntimeException(); }"


def render_class(c, nested, indent=""):
    out = []
    ind = indent + "    "
    mods = [x for x in c.mods if x in ("public", "abstract", "final")]
    if c.kind in ("interface", "enum"):
        mods = [x for x in mods if x not in ("abstract", "final")]
    if indent:
        mods = [x for x in mods if x != "final"]
        if c.kind == "class":
            mods.append("static")
    if "public" not in mods:
        mods.insert(0, "public")
    kw = c.kind
    head = "%s%s %s %s%s" % (indent, " ".join(mods), kw, c.simple, jtype(c.typeparams))
    if c.extends and c.kind == "class":
        head += " extends " + jtype(c.extends[0])
    if c.implements:
        head += (" extends " if c.kind == "interface" else " implements ") + ", ".join(jtype(i) for i in c.implements)
    out.append(head + " {")
    if c.kind == "enum":
        out.append(ind + (", ".join(c.enum_constants) if c.enum_constants else "") + ";")
    abstract_methods = [m for m in c.members if m.kind == "method" and "abstract" in m.mods]
    keep_abstract = c.kind == "interface" and len(abstract_methods) == 1
    has_noarg = any(m.kind == "ctor" and not m.params for m in c.members)
    seen_fields = set()
    for m in c.members:
        if m.kind == "field":
            if m.name in seen_fields:
                continue
            seen_fields.add(m.name)
            fm = [x for x in m.mods if x in ("public", "protected", "private", "static")]
            if c.kind == "interface":
                out.append("%spublic static final %s %s = %s;" % (ind, jtype(m.rtype), m.name, default_value(jtype(m.rtype))))
            else:
                out.append("%s%s %s %s;" % (ind, " ".join(fm), jtype(m.rtype), m.name))
        elif m.kind == "ctor":
            if c.kind == "interface":
                continue
            pm = [x for x in m.mods if x in ("public", "protected", "private")]
            params = ", ".join("%s p%d" % (jtype(p), i) for i, p in enumerate(m.params))
            th = (" throws " + ", ".join(jtype(t) for t in m.throws)) if m.throws else ""
            out.append("%s%s %s%s(%s)%s { }" % (ind, " ".join(pm), (jtype(m.typeparams) + " ") if m.typeparams else "", c.simple, params, th))
        else:
            mm = [x for x in m.mods if x in ("public", "protected", "private", "static", "final", "default")]
            params = ", ".join("%s p%d" % (jtype(p), i) for i, p in enumerate(m.params))
            th = (" throws " + ", ".join(jtype(t) for t in m.throws)) if m.throws else ""
            tp = (jtype(m.typeparams) + " ") if m.typeparams else ""
            if c.kind == "interface":
                if "static" in mm:
                    mm = [x for x in mm if x not in ("default", "final")]
                    out.append("%s%s %s%s %s(%s)%s %s" % (ind, " ".join(mm), tp, jtype(m.rtype), m.name, params, th, body_for(m)))
                elif keep_abstract and "abstract" in m.mods:
                    out.append("%spublic %s%s %s(%s)%s;" % (ind, tp, jtype(m.rtype), m.name, params, th))
                else:
                    mm = [x for x in mm if x not in ("default", "final", "public", "private")]
                    out.append("%spublic default %s%s %s(%s)%s %s" % (ind, tp, jtype(m.rtype), m.name, params, th, body_for(m)))
            else:
                if c.kind == "enum":
                    mm = [x for x in mm if x != "final"]
                out.append("%s%s %s%s %s(%s)%s %s" % (ind, " ".join(mm), tp, jtype(m.rtype), m.name, params, th, body_for(m)))
    if c.kind == "class" and not has_noarg:
        out.append("%sprotected %s() { }" % (ind, c.simple))
    for n in nested:
        out.extend(render_tree(n, ind))
    out.append(indent + "}")
    return out


class Node:
    def __init__(self, cls):
        self.cls = cls
        self.children = []


def render_tree(node, indent=""):
    return render_class(node.cls, node.children, indent)


def generate(version):
    dump = os.path.join(PROBE, version + ".txt")
    classes = parse_dump(dump)
    for c in classes.values():
        dedupe(c)
    refs, iface = collect_refs(classes)
    allc = dict(classes)
    for name in HINTS:
        if name not in classes:
            refs.setdefault(name, 0)
    code_fields = parse_code_fields(dump)
    for name in code_fields:
        if name not in classes:
            refs.setdefault(name, 0)
    for name, arity in refs.items():
        if name in allc:
            continue
        arity = max(arity, HINT_ARITY.get(name, 0))
        tp = ""
        if arity:
            tp = "<" + ", ".join("T%d" % i for i in range(arity)) + ">"
        c = Cls(name, "interface" if name in iface or name in HINT_INTERFACES else "class", ["public"], tp, [], [])
        c.probed = False
        if name in HINTS:
            sup, members = HINTS[name]
            if sup:
                c.extends = [sup]
            for line in members:
                m = parse_member(line, c)
                if m:
                    c.members.append(m)
        if name in HINT_IMPLEMENTS and not c.implements:
            c.implements = list(HINT_IMPLEMENTS[name])
        for line in code_fields.get(name, []):
            m = parse_member(line, c)
            if m:
                c.members.append(m)
        allc[name] = c
    # ensure outers exist for nested
    for name in list(allc):
        parts = name.split("$")
        for i in range(1, len(parts)):
            outer = "$".join(parts[:i])
            if outer not in allc:
                c = Cls(outer, "class", ["public"], "", [], [])
                c.probed = False
                allc[outer] = c
    nodes = {n: Node(c) for n, c in allc.items()}
    roots = []
    for n, node in nodes.items():
        if "$" in n:
            nodes[n.rsplit("$", 1)[0]].children.append(node)
        else:
            roots.append(node)
    src = os.path.join(OUT, version, "src")
    if os.path.isdir(src):
        subprocess.run(["rm", "-rf", src], check=True)
    for node in roots:
        fq = node.cls.fq
        pkg, simple = fq.rsplit(".", 1)
        d = os.path.join(src, *pkg.split("."))
        os.makedirs(d, exist_ok=True)
        with open(os.path.join(d, simple + ".java"), "w") as f:
            f.write("package %s;\n\n" % pkg)
            f.write("\n".join(render_tree(node)) + "\n")
    return src


def compile_stubs(version):
    src = generate(version)
    classes = os.path.join(OUT, version, "classes")
    subprocess.run(["rm", "-rf", classes], check=True)
    files = []
    for dp, _, fs in os.walk(src):
        files += [os.path.join(dp, f) for f in fs if f.endswith(".java")]
    lst = os.path.join(OUT, version, "files.txt")
    with open(lst, "w") as f:
        f.write("\n".join(files))
    r = subprocess.run([os.path.join(TC, "jre", "bin", "java"), "-jar", os.path.join(TC, "ecj.jar"),
                        "-source", "21", "-target", "21", "-nowarn", "-proceedOnError", "-d", classes, "@" + lst],
                       capture_output=True, text=True)
    errs = r.stdout + r.stderr
    n = len(re.findall(r"^\d+\. ERROR", errs, re.M))
    with open(os.path.join(OUT, version, "stub_errors.txt"), "w") as f:
        f.write(errs)
    print("%-8s %4d stub files, %d stub compile errors" % (version, len(files), n))
    return classes


def versions():
    vs = [f[:-4] for f in os.listdir(PROBE) if f[0].isdigit() and f.endswith(".txt")]
    return sorted(vs, key=lambda v: [int(x) for x in v.split(".")])


if __name__ == "__main__":
    args = sys.argv[1:]
    targets = versions() if args == ["--all"] else args
    for v in targets:
        compile_stubs(v)
