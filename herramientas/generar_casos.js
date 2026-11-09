/* Genera "casos de oro": corre el núcleo ORIGINAL de Mi plata (index.html, JavaScript)
   sobre muchos estados y fechas, y guarda los resultados. La app Kotlin tiene que dar lo mismo.
   Uso: node generar_casos.js <ruta/index.html> <salida.json> [copia1.json copia2.json ...]
   Sin copias reales, solo genera estados de prueba inventados (con semilla fija). */
const fs = require('fs');
const [,, rutaHtml, salida, ...copias] = process.argv;
const html = fs.readFileSync(rutaHtml, 'utf8');
const js = html.slice(html.indexOf('<script>') + 8, html.lastIndexOf('</script>'));
const ini = js.indexOf('const MS_DIA'), fin = js.indexOf('/* =====================================================================\n   02b');
if (!(ini > 0 && fin > ini)) throw new Error('No se encontró el núcleo');
const N = new Function(js.slice(ini, fin) + `
return { calcular, diasDelPeriodo, semaforo, conciliar, rachaEnVerde, objetivoDiario, numeroDeDia, isoDeNumero, mesSiguiente,
         migrarEstado, aporteMensualSugerido, juntadoEnMeta, iniciarPeriodo, congelarDias };`)();

/* Generador pseudoaleatorio con semilla (mulberry32) */
let semilla = 12345;
const rnd = () => { semilla |= 0; semilla = semilla + 0x6D2B79F5 | 0; let t = Math.imul(semilla ^ semilla >>> 15, 1 | semilla);
  t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t; return ((t ^ t >>> 14) >>> 0) / 4294967296; };
const ent = (a, b) => a + Math.floor(rnd() * (b - a + 1));
const prob = p => rnd() < p;
const elegir = l => l[ent(0, l.length - 1)];
const monto = (a, b) => prob(0.3) ? ent(a, b) + ent(0, 99) / 100 : ent(a / 100, b / 100) * 100;

function estadoInventado() {
  const n0 = N.numeroDeDia('2026-09-01') + ent(0, 400), start = N.isoDeNumero(n0);
  const end = prob(0.85) ? N.mesSiguiente(start) : N.isoDeNumero(n0 + ent(5, 40));
  const dias = N.numeroDeDia(end) - n0;
  const fecha = (a = -3, b = dias + 3) => N.isoDeNumero(n0 + ent(a, b));
  const S = { sueldo: monto(300000, 2500000), previo: prob(0.4) ? monto(0, 80000) : 0, start, end, items: [], spent: [], recal: null };
  for (let k = ent(0, 9); k > 0; k--) {
    const i = { n: 'Gasto ' + k, a: monto(5000, 300000), p: prob(0.7) };
    if (prob(0.3)) i.d = fecha(0, dias);
    if (prob(0.15)) { i.x = [{ amt: monto(1000, 50000), desde: fecha(0, dias) }]; i.a += i.x[0].amt; }
    if (prob(0.1)) i.a = String(i.a);
    S.items.push(i);
  }
  let id = 1;
  for (let k = ent(0, 70); k > 0; k--) {
    const e = { id: id++, date: fecha(), amt: prob(0.1) ? -monto(500, 20000) : monto(100, 30000), note: 'g' };
    if (prob(0.05)) e.meta = 1; else if (prob(0.08)) e.dev = elegir([1, 2]);
    S.spent.push(e);
  }
  if (prob(0.25)) S.ajustes = [{ id: 1, date: fecha(0, dias), desde: fecha(0, dias), amt: (prob(0.5) ? -1 : 1) * monto(1000, 60000), note: 'a' }];
  if (prob(0.2)) S.recal = { day: ent(0, dias), base: monto(5000, 40000), obj: prob(0.5) };
  if (prob(0.3)) { S.objetivo = monto(5000, 30000); if (prob(0.5)) S.objetivoDesde = fecha(0, dias); }
  if (prob(0.5)) {
    S.modoFondo = true; if (prob(0.8)) S.fondoDesde = fecha(-2, dias);
    if (prob(0.3)) S.fondoExceso = elegir(['aparte', 'siguiente', 'fondo']);
    if (prob(0.4)) S.fondoPrev = monto(0, 100000);
    if (prob(0.4)) S.fondoUsos = [{ id: 1, date: fecha(0, dias), amt: monto(1000, 20000), para: elegir(['hoy', 'cubre']) },
                                  { id: 2, date: fecha(0, dias), amt: monto(1000, 9000), para: 'hoy' }];
  }
  if (prob(0.5)) { S.rendTasa = elegir([19, 19.7, 35, 0, 400]);
    if (prob(0.7)) { S.rendOn = true; S.rendDesde = fecha(-5, dias); S.rendPrev = prob(0.5) ? monto(0, 5000) : 0; S.rendEnMP = prob(0.5); }
    if (prob(0.6)) { S.rendDispOn = true; S.rendDispDesde = fecha(-5, dias); } }
  if (prob(0.5)) S.cuentas = [{ n: 'MP', m: monto(0, 900000) }, { n: 'Banco', m: monto(0, 90000), x: prob(0.3) }];
  if (prob(0.3)) S.aviso = elegir([10, 20, 35]);
  return S;
}

const r6 = v => typeof v === 'number' ? v : v;
function evaluar(S, hoy) {
  const c = N.calcular(S, hoy);
  const sem = N.semaforo(c, S);
  return {
    total: c.total, porPagar: c.porPagar, disp: c.disp, dias: c.dias, hoyIdx: c.hoyIdx, base: c.base, rendDisp: c.rendDisp,
    baseHoy: c.baseHoy, baseManana: c.baseManana, manana: c.manana, gastoHoy: c.gastoHoy, quedaHoy: c.quedaHoy,
    faltan: c.faltan, saldo: c.saldo, gastadoAntesDeHoy: c.gastadoAntesDeHoy, recalDia: c.recal ? c.recal.day : null,
    filas: c.filas && c.filas.map(f => [f.presupuesto, f.gasto, f.sobra, f.aporte]),
    fondo: c.fondo.activo ? { ahorroPeriodo: c.fondo.ahorroPeriodo, usos: c.fondo.usos, total: c.fondo.total, rend: c.fondo.rend,
      rendPend: c.fondo.rendPend, totalConRend: c.fondo.totalConRend, acumulado: c.fondo.acumulado, hoySuma: c.fondo.hoySuma,
      disponible: c.fondo.disponible } : null,
    dias_: N.diasDelPeriodo(S, hoy).map(x => [x.iso, x.presupuesto, x.gasto, x.sobra]),
    semaforo: sem[0], conciliar: N.conciliar(S, c), racha: N.rachaEnVerde(S, hoy), objetivo: N.objetivoDiario(S, hoy),
  };
}

const casos = [];
function agregar(nombre, S) {
  N.migrarEstado(S);
  const n0 = N.numeroDeDia(S.start), dias = N.numeroDeDia(S.end) - n0;
  const fechas = new Set([-2, 0, 1, Math.floor(dias / 2), dias - 1, dias, dias + 3].map(d => N.isoDeNumero(n0 + d)));
  for (let k = 0; k < 4; k++) fechas.add(N.isoDeNumero(n0 + ent(0, Math.max(0, dias - 1))));
  for (const hoy of fechas) {
    const copia = JSON.parse(JSON.stringify(S));
    casos.push({ nombre, hoy, estado: copia, esperado: evaluar(JSON.parse(JSON.stringify(S)), hoy) });
  }
}
for (const ruta of copias) {
  const S = JSON.parse(fs.readFileSync(ruta, 'utf8'));
  const viejas = S.copias || []; delete S.copias;
  agregar('real:' + ruta, S);
  viejas.forEach(c => agregar('real-copia:' + c.iso, JSON.parse(c.json)));
}
if (!copias.length) for (let k = 0; k < 150; k++) agregar('inventado-' + k, estadoInventado());
fs.writeFileSync(salida, JSON.stringify(casos));
console.log(casos.length + ' casos guardados en ' + salida);

/* ---- Casos de "período nuevo" y edición de montos: estado antes → estado después (JS original) ---- */
if (process.env.PERIODO) {
  const G = new Function(js.slice(ini, fin) + `
const CAMBIAR_MONTO = (S, k, v, hoy) => {
  const it = S.items[k], antes = montoDe(it), nuevo = v;
  if (numeroDeDia(hoy) >= numeroDeDia(S.start) && nuevo !== antes) {
    const desde = isoDeNumero(Math.min(numeroDeDia(hoy), numeroDeDia(S.end) - 1));
    if (antes === 0) { it.d = desde; delete it.x; }
    else if (nuevo > antes) (it.x = it.x || []).push({ amt: redondear2(nuevo - antes), desde });
    else { let baja = antes - nuevo;
      while (baja > 0 && it.x && it.x.length) { const u = it.x[it.x.length - 1];
        if (u.amt > baja) { u.amt = redondear2(u.amt - baja); baja = 0; } else { baja -= u.amt; it.x.pop(); } }
      if (it.x && !it.x.length) delete it.x; }
  }
  it.a = nuevo;
};
return { iniciarPeriodo, migrarEstado, CAMBIAR_MONTO, numeroDeDia, isoDeNumero, mesSiguiente };`)();
  const salidaP = [];
  for (let k = 0; k < 300; k++) {
    const S = estadoInventado();
    S.items.forEach((i, j) => {
      if (prob(0.25)) i.q = { actual: ent(1, 12), total: ent(1, 12) };
      if (prob(0.3)) i.f = prob(0.5);
      if (prob(0.3)) i.v = prob(0.5);
      if (prob(0.2)) i.prox = prob(0.2) ? '' : monto(1000, 90000);
      if (prob(0.15)) i.ipc = { prox: '2027-03', cada: 6, partes: 2, ...(prob(0.6) ? { pend: { desde: elegir(['2026-11', '2027-03', '2028-01']), monto: monto(1000, 200000) } } : {}) };
      if (prob(0.15)) i.mp = 'alias.' + j;
    });
    if (prob(0.4)) S.proximos = [{ n: 'Nuevo', a: monto(1000, 50000) }, { n: '', a: 0 }, { n: '', a: '3500' }];
    if (prob(0.3)) S.meDeben = [{ id: 9, date: S.start, amt: 1234, note: 'x' }];
    if (prob(0.3)) S.hist = [{ start: '2026-01-01', end: '2026-02-01', sueldo: 1, previo: 0, importantes: 0, gastado: 0, quedo: 1 }];
    if (prob(0.3)) S.fondoRetirado = monto(0, 20000);
    S.cierres = { [S.start]: { b: 1, g: 0, l: 1 } };
    G.migrarEstado(S);
    const n0 = G.numeroDeDia(S.start), dias = G.numeroDeDia(S.end) - n0;
    const hoy = G.isoDeNumero(n0 + ent(-2, dias + 2));
    // edición de monto
    const S1 = JSON.parse(JSON.stringify(S)), cambios = [];
    for (let m = 0; m < 3 && S1.items.length; m++) {
      const kk = ent(0, S1.items.length - 1), v = prob(0.2) ? 0 : monto(0, 300000);
      G.CAMBIAR_MONTO(S1, kk, v, hoy); cambios.push([kk, v]);
    }
    // período nuevo
    const S2 = JSON.parse(JSON.stringify(S));
    const inicio = S.end, finP = G.mesSiguiente(S.end), previo = prob(0.5) ? monto(0, 90000) : 0;
    const terminadas = G.iniciarPeriodo(S2, hoy, { previo, inicio, fin: finP });
    salidaP.push({ hoy, antes: S, cambios, trasCambios: S1, periodo: { previo, inicio, fin: finP }, despues: S2, terminadas });
  }
  fs.writeFileSync(process.env.PERIODO, JSON.stringify(salidaP));
  console.log(salidaP.length + ' casos de período en ' + process.env.PERIODO);
}
