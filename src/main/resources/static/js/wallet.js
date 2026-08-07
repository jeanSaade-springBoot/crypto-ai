let chart;

const byId = id => document.getElementById(id);
const n = value => Number(value || 0);
const money = value => `${n(value).toLocaleString('en-US', {minimumFractionDigits: 2, maximumFractionDigits: 8})} USDT`;
const qty = value => n(value).toLocaleString('en-US', {maximumFractionDigits: 12});
const pnlClass = value => n(value) >= 0 ? 'positive' : 'negative';

async function api(url, options) {
    const response = await fetch(url, options);
    if (!response.ok) {
        let message = 'Request failed';
        try {
            const json = await response.json();
            message = json.message || json.error || message;
        } catch (_) {
            const text = await response.text();
            if (text) message = text;
        }
        throw new Error(message);
    }
    return response.status === 204 ? null : response.json();
}

async function load() {
    try {
        const data = await api('/api/wallet');
        byId('status').textContent = data.portfolioStatus || 'NOT STARTED';
        byId('status').className = data.portfolioStatus === 'WINNING' ? 'positive' : data.portfolioStatus === 'LOSING' ? 'negative' : '';
        byId('portfolio').textContent = money(data.portfolioValueUsdt);
        byId('invested').textContent = `Net invested ${money(data.netInvestedUsdt)}`;
        setPnl('total-pnl', data.totalPnlUsdt);
        byId('total-return').textContent = `${n(data.totalReturnPercent).toFixed(2)}% since start`;
        byId('change24h').textContent = `24h ${n(data.change24hUsdt) >= 0 ? '+' : ''}${money(data.change24hUsdt)}`;
        byId('available').textContent = money(data.availableUsdt);
        setPnl('realized', data.realizedPnlUsdt);
        setPnl('unrealized', data.unrealizedPnlUsdt);

        const settings = data.settings || {};
        const configuredMaximum = Number(settings.maximumDailyNewPositions ?? 0);
        byId('daily-limit-enabled').checked = configuredMaximum > 0;
        byId('maximum-daily-positions').value = configuredMaximum > 0 ? configuredMaximum : 6;
        byId('base-trade-amount').value = settings.baseTradeAmountUsdt || 100;
        updateDailyLimitField();
        byId('minimum-reserve').value = settings.minimumUsdtReserve || 0;

        const daily = data.dailyTrading || {};
        byId('daily-budget').textContent = money(daily.dailyTradeBudgetUsdt);
        const unlimited = Boolean(daily.unlimited) || Number(daily.maximumNewPositions) === 0;
        byId('daily-buys').textContent = unlimited
            ? `${n(daily.executedBuys)} / Unlimited`
            : `${n(daily.executedBuys)} / ${n(daily.maximumNewPositions)}`;
        byId('remaining-buys').textContent = unlimited
            ? 'No daily transaction limit'
            : `${n(daily.remainingBuys)} remaining`;
        byId('budget-state').textContent = daily.budgetLocked
            ? 'Locked for today; SELL proceeds do not resize it'
            : 'Preview; locks when the first BUY executes';

        byId('asset-body').innerHTML = (data.assets || []).map(asset => `
            <tr>
                <td><strong>${asset.symbol}</strong></td>
                <td>${qty(asset.quantity)}</td>
                <td>${asset.symbol === 'USDT' ? '—' : money(asset.averageBuyPriceUsdt)}</td>
                <td>${money(asset.currentPriceUsdt)}</td>
                <td>${money(asset.costBasisUsdt)}</td>
                <td>${money(asset.currentValueUsdt)}</td>
                <td class="${pnlClass(asset.unrealizedPnlUsdt)}">${money(asset.unrealizedPnlUsdt)} <small>${n(asset.unrealizedPnlPercent).toFixed(2)}%</small></td>
            </tr>`).join('') || '<tr><td colspan="7">No assets</td></tr>';

        byId('trade-body').innerHTML = (data.trades || []).map(trade => `
            <tr>
                <td>${new Date(trade.executedAt).toLocaleString()}</td>
                <td>${trade.signalId || '—'}</td>
                <td>${trade.symbol}</td>
                <td><span class="badge ${trade.side === 'BUY' ? 'positive' : 'negative'}">${trade.side}</span></td>
                <td>${qty(trade.quantity)}</td>
                <td>${money(trade.priceUsdt)}</td>
                <td>${money(trade.netAmountUsdt)}</td>
                <td class="${pnlClass(trade.realizedPnlUsdt)}">${trade.realizedPnlUsdt == null ? '—' : money(trade.realizedPnlUsdt)}</td>
            </tr>`).join('') || '<tr><td colspan="8">No automatic trades yet</td></tr>';

        renderChart(data.snapshots || []);
    } catch (error) {
        showMessage(error.message, true);
    }
}

function setPnl(id, value) {
    const element = byId(id);
    element.textContent = `${n(value) >= 0 ? '+' : ''}${money(value)}`;
    element.className = pnlClass(value);
}

function renderChart(snapshots) {
    const options = {
        chart: {type: 'line', height: 320, toolbar: {show: false}},
        series: [
            {name: 'Portfolio value', data: snapshots.map(x => [new Date(x.capturedAt).getTime(), n(x.portfolioValueUsdt)])},
            {name: 'Net invested', data: snapshots.map(x => [new Date(x.capturedAt).getTime(), n(x.netInvestedUsdt)])}
        ],
        xaxis: {type: 'datetime'},
        yaxis: {labels: {formatter: value => value.toFixed(0)}},
        stroke: {curve: 'smooth', width: 3},
        noData: {text: 'Add a USDT deposit to start'}
    };
    if (chart) {
        chart.updateSeries(options.series, false);
        return;
    }
    chart = new ApexCharts(byId('wallet-chart'), options);
    chart.render();
}

function showMessage(message, isError = false) {
    const element = byId('message');
    element.textContent = message;
    element.classList.remove('hidden', 'success');
    element.classList.toggle('success', !isError);
    element.scrollIntoView({behavior: 'smooth', block: 'nearest'});
    window.setTimeout(() => element.classList.add('hidden'), 4500);
}

async function save(url, method, body, successMessage) {
    try {
        await api(url, {
            method,
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(body)
        });
        await load();
        showMessage(successMessage);
        return true;
    } catch (error) {
        showMessage(error.message, true);
        return false;
    }
}


function updateDailyLimitField() {
    const enabled = byId('daily-limit-enabled').checked;
    byId('maximum-daily-positions').disabled = !enabled;
    byId('daily-limit-field').classList.toggle('disabled-field', !enabled);
}

byId('daily-limit-enabled').addEventListener('change', updateDailyLimitField);

byId('settings-form').addEventListener('submit', async event => {
    event.preventDefault();
    await save('/api/wallet/settings', 'PUT', {
        maximumDailyNewPositions: byId('daily-limit-enabled').checked
            ? byId('maximum-daily-positions').value
            : 0,
        baseTradeAmountUsdt: byId('base-trade-amount').value,
        minimumUsdtReserve: byId('minimum-reserve').value
    }, 'Trading configuration saved successfully.');
});

byId('cash-form').addEventListener('submit', async event => {
    event.preventDefault();
    const type = byId('flow-type').value;
    const amount = byId('flow-amount').value;
    const saved = await save('/api/wallet/cash-flows', 'POST', {
        flowType: type,
        amountUsdt: amount,
        notes: byId('flow-notes').value
    }, `${type === 'DEPOSIT' ? 'Deposit' : 'Withdrawal'} of ${amount} USDT saved successfully.`);
    if (saved) {
        byId('flow-amount').value = '';
        byId('flow-notes').value = '';
    }
});

byId('asset-form').addEventListener('submit', async event => {
    event.preventDefault();
    const symbol = byId('asset-symbol').value;
    const quantityValue = byId('asset-quantity').value;
    const averageValue = byId('asset-average').value;

    if (!symbol) {
        showMessage('Please choose a coin.', true);
        return;
    }

    const saved = await save('/api/wallet/assets', 'POST', {
        symbol,
        quantity: quantityValue,
        averageBuyPriceUsdt: averageValue
    }, `${symbol} holding saved successfully: ${quantityValue} ${symbol} at an average entry of ${averageValue} USDT.`);

    if (saved) {
        byId('asset-symbol').value = '';
        byId('asset-quantity').value = '';
        byId('asset-average').value = '';
    }
});

byId('refresh').addEventListener('click', load);
load();
